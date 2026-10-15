package com.sh11fty.allbanks

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sh11fty.allbanks.ui.AppTheme
import com.sh11fty.allbanks.ui.MainScaffold
import com.sh11fty.allbanks.ui.MainVm

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(true)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if ((application as AllBanksApp).prefs.getBoolean("blockScreenshots", false)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        setContent {
            AppTheme {
                if (locked) {
                    Column(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Lock, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("All Banks", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Locked to keep your finances private", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { authenticate() }, modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp).height(52.dp)) { Text("Unlock") }
                    }
                } else {
                    val vm: MainVm = viewModel()
                    MainScaffold(vm)
                }
            }
        }
        authenticate()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 60_000) {
            locked = true
            authenticate()
        }
    }

    private fun authenticate() {
        val allowed = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No screen lock set up on the phone: nothing to authenticate with.
            locked = false
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock All Banks")
                .setAllowedAuthenticators(allowed)
                .build()
        )
    }
}
