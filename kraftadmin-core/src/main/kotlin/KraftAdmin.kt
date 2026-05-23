package com.kraftadmin

import com.kraftadmin.config.KraftAdminConfig
import org.slf4j.LoggerFactory

object KraftAdmin {
    val logger = LoggerFactory.getLogger(KraftAdmin::class.java)

    private var started = true

    fun start(config: KraftAdminConfig) {

        logger.info("Starting KraftAdmin with config: $config")

//        started = true
        logger.info("KraftAdmin started successfully")
    }

    fun isStarted(): Boolean = started

    fun stop() {
        if (!started) return
        logger.info("Stopping KraftAdmin")
        started = false
        logger.info("KraftAdmin stopped")
    }

}