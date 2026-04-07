package com.kraftadmin.util

import com.kraftadmin.utils.telementary.KraftTelemetryEvent
import com.kraftadmin.utils.telementary.KraftTelemetryService
import com.kraftadmin.utils.telementary.TelemetryType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import security.AdminSecurityProvider
import security.SecurityProviderChain

@Component
class SpringGlobalBIInterceptor(
    private val telemetryService: KraftTelemetryService,
    private val securityChain: SecurityProviderChain
) : HandlerInterceptor {

    private val logger = LoggerFactory.getLogger(SpringGlobalBIInterceptor::class.java)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        request.setAttribute("startTime", System.currentTimeMillis())
        return true
    }

    override fun afterCompletion(request: HttpServletRequest, response: HttpServletResponse, handler: Any, ex: Exception?) {
        logger.info("afterCompletion request ${request.method} ${request.requestURI} started")
        val startTime = request.getAttribute("startTime") as Long
        val duration = System.currentTimeMillis() - startTime

        val currentUser = securityChain.resolveCurrentUser()?.username ?: "anonymous"

        telemetryService.record(
            KraftTelemetryEvent(
                type = TelemetryType.SYSTEM,
                resource = request.requestURI,
                action = request.method,
                durationMs = duration,
                status = response.status,
                actor = currentUser,
                // New BI Fields
                ipAddress = request.remoteAddr,
                userAgent = request.getHeader("User-Agent")
            )
        )
        logger.info("afterCompletion request ${request.method} ${request.requestURI} completed with $duration ms ms")
    }



}