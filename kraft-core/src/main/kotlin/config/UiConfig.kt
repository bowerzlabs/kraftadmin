package com.kraftadmin.config

data class UiConfig(
    val title: String = "KraftAdmin",
    val theme: Theme = Theme.AUTO,
    val icon: String = "ic_launcher",
) {
    enum class Theme {
        LIGHT, DARK, AUTO
    }
}
