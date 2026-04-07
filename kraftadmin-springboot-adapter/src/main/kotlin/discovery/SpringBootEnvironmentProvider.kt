package com.kraftadmin.discovery

import com.kraftadmin.spi.KraftEnvironmentProvider

class SpringBootEnvironmentProvider : KraftEnvironmentProvider {

    override fun getAuthMode(): String =
        if (isSpringSecurityActive()) "bridge" else "standalone"

    override fun getShouldShowLogout(): Boolean =
        !isSpringSecurityActive()

    override fun getEnvironmentName(): String = "Production"

    private fun isSpringSecurityActive(): Boolean = try {
        Class.forName("org.springframework.security.web.SecurityFilterChain", false, javaClass.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}