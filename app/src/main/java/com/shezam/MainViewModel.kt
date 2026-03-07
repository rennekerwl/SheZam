package com.shezam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shezam.core.FingerprintDatabase
import com.shezam.core.FingerprintToken
import com.shezam.core.LegacyFingerprinter
import com.shezam.core.MatchResult
import com.shezam.core.SheBopMatcher
import com.shezam.core.SimpleFingerprinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Idle : UiState
    data object Listening : UiState
    data object Processing : UiState
    data class Result(
        val isMatch: Boolean,
        val confidence: Double,
        val votes: Int,
        val lowQuality: Boolean,
    ) : UiState

    data class Error(val message: String) : UiState
}

class MainViewModel : ViewModel() {
    private val fingerprinter = SimpleFingerprinter()
    private val matcher = SheBopMatcher()
    private val legacyFingerprinter = LegacyFingerprinter()

    private var sheBopReference: List<FingerprintToken> = emptyList()
    private var useLegacyFingerprinter = false

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadReference(csv: String) {
        sheBopReference = FingerprintDatabase.fromCsv(csv)
        useLegacyFingerprinter = sheBopReference.isNotEmpty() && sheBopReference.all { it.deltaFrames == 1 }
    }

    fun analyze(samples: ShortArray) {
        viewModelScope.launch {
            _uiState.value = UiState.Processing
            val result = withContext(Dispatchers.Default) {
                val lowQuality = !fingerprinter.detectQuality(samples)
                val observed = if (useLegacyFingerprinter) {
                    legacyFingerprinter.fingerprint(samples)
                } else {
                    fingerprinter.fingerprint(samples)
                }
                val match = matcher.match(observed, sheBopReference)
                match to lowQuality
            }
            publishResult(result.first, result.second)
        }
    }

    fun setListening() {
        _uiState.value = UiState.Listening
    }

    fun setError(message: String) {
        _uiState.value = UiState.Error(message)
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }

    private fun publishResult(match: MatchResult, lowQuality: Boolean) {
        _uiState.value = UiState.Result(
            isMatch = match.isMatch,
            confidence = match.confidence,
            votes = match.strongestOffsetVotes,
            lowQuality = lowQuality,
        )
    }
}
