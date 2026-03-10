package com.droidclaw.ui.state

sealed interface UiScreenState {
    data object Loading : UiScreenState
    data object Content : UiScreenState
    data class Empty(val hint: String? = null) : UiScreenState
    data class Error(val message: String) : UiScreenState
    data object Offline : UiScreenState
}
