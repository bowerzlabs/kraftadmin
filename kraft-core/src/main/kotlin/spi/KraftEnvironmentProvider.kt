package com.kraftadmin.spi

interface KraftEnvironmentProvider {
    fun getAuthMode(): String
    fun getShouldShowLogout(): Boolean
    fun getEnvironmentName(): String
}