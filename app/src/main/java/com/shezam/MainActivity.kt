package com.shezam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val audioCapture = AudioCapture()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                listen()
            } else {
                viewModel.setError("Microphone permission denied.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val csv = assets.open("she_bop_fingerprints.csv").bufferedReader().use { it.readText() }
        viewModel.loadReference(csv)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsState()
                    SheZamScreen(
                        uiState = state,
                        onListen = ::requestOrStartListening,
                        onRetry = viewModel::reset,
                    )
                }
            }
        }
    }

    private fun requestOrStartListening() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) listen() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun listen() {
        viewModel.setListening()
        lifecycleScope.launch {
            val captured = audioCapture.capture()
            captured
                .onSuccess { viewModel.analyze(it) }
                .onFailure { viewModel.setError(it.message ?: "Audio capture failed.") }
        }
    }
}

@Composable
private fun SheZamScreen(
    uiState: UiState,
    onListen: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SheZam", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        when (uiState) {
            UiState.Idle -> {
                Text("Tap Listen to capture 5 seconds of audio.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onListen) { Text("Listen") }
            }

            UiState.Listening -> {
                Text("Listening…")
            }

            UiState.Processing -> {
                Text("Processing…")
            }

            is UiState.Result -> {
                ResultCard(uiState)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Try Again") }
            }

            is UiState.Error -> {
                Text("Error: ${uiState.message}")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun ResultCard(state: UiState.Result) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            val title = if (state.isMatch) {
                "✅ This sounds like She Bop"
            } else {
                "❌ This does not match She Bop"
            }
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Confidence: ${"%.2f".format(state.confidence)}")
            Text("Votes: ${state.votes}")
            if (state.lowQuality) {
                Spacer(Modifier.height(8.dp))
                Text("Capture quality was low. Try moving closer to the speaker.")
            }
        }
    }
}
