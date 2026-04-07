package com.kraftadmin.config

sealed interface PersistenceConfig {

    object None : PersistenceConfig

    data class Jdbc(
        val dialect: Dialect,
        val schema: String? = null
    ) : PersistenceConfig

    enum class Dialect {
        POSTGRES,
        MYSQL,
        SQLITE,
        H2
    }
}
