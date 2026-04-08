package com.kraftadmin.json

/**
 * Framework-agnostic JSON abstraction.
 * Each adapter provides its own implementation.
 */
interface KraftJsonSerializer {
    fun toJson(value: Any?): String
    fun <T> fromJson(json: String, type: Class<T>): T
    fun toMap(value: Any?): Map<String, Any?>
}