package com.kraftadmin.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.kraftadmin.json.KraftJsonSerializer

class JacksonKraftJsonSerializer(
    private val mapper: ObjectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerModule(kotlinModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
) : KraftJsonSerializer {

    override fun toJson(value: Any?): String = mapper.writeValueAsString(value)

    override fun <T> fromJson(json: String, type: Class<T>): T = mapper.readValue(json, type)

    override fun toMap(value: Any?): Map<String, Any?> =
        mapper.convertValue(value, mapper.typeFactory.constructMapType(Map::class.java, String::class.java, Any::class.java))
}