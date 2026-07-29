package com.nuvio.app.features.simkl

fun handleSimklAuthCallbackUrl(url: String) {
    SimklAuthRepository.onAuthCallbackReceived(url)
}
