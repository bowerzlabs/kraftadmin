package com.kraftadmin.spi

interface EntityDiscoverer {
    fun discover(): Set<Class<*>>
    val name: String
}