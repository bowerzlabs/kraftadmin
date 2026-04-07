package com.kraftadmin.config

import com.kraftadmin.spi.KraftAdminResource

class KraftAdminRuntimeConfig {
    lateinit var config: KraftAdminConfig
        private set

    val resourcesByName: Map<String, KraftAdminResource<*>>
        get() {
            val associateBy = config.generatedResources.associateBy { it.name }
            return associateBy
        }

    fun set(config: KraftAdminConfig) {
        this.config = config
    }

}

