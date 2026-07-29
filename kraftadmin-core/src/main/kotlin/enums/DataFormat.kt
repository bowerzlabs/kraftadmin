package com.kraftadmin.enums

enum class DataFormat(val contentType: String, val fileExtension: String) {
    JSON("application/json", "json"),
    CSV("text/csv", "csv"),
    XML("application/xml", "xml");

    companion object {
        fun fromStringOrNull(value: String): DataFormat? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}