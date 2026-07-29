package com.nuvio.app.features.simkl

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioLoadingIndicator

@Composable
internal fun SimklConnectionCard(
    isTablet: Boolean,
    uiState: SimklAuthUiState,
) {
    val uriHandler = LocalUriHandler.current
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val verticalPadding = if (isTablet) 18.dp else 16.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (uiState.mode) {
            SimklConnectionMode.CONNECTED -> {
                Text(
                    text = "Connected as ${uiState.username ?: "Simkl User"}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Your watch progress, ratings, and list statuses will sync with Simkl.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = SimklAuthRepository::disconnect,
                    enabled = !uiState.isConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    if (uiState.isConnecting) {
                        NuvioLoadingIndicator(
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text("Disconnect")
                    }
                }
            }

            SimklConnectionMode.DISCONNECTED -> {
                Text(
                    text = "Sign in to Simkl to sync your movies, TV shows, and anime progress.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val authUrl = SimklAuthRepository.onConnectRequested() ?: return@Button
                        runCatching { uriHandler.openUri(authUrl) }
                            .onFailure {
                                SimklAuthRepository.onAuthLaunchFailed(
                                    it.message ?: "Failed to open browser",
                                )
                            }
                    },
                    enabled = uiState.credentialsConfigured && !uiState.isConnecting,
                ) {
                    if (uiState.isConnecting) {
                        NuvioLoadingIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text("Connect Simkl")
                    }
                }
                if (!uiState.credentialsConfigured) {
                    Text(
                        text = "Simkl API key missing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        uiState.statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        uiState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
