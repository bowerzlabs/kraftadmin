package com.kraftadmin.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.kraftadmin.json.KraftJsonSerializer

class JacksonKraftJsonSerializer(
    private val mapper: ObjectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerModule(kotlinModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        // Register Hibernate module only if Hibernate is on the consumer's classpath.
        // compileOnly in the adapter means we can reference the class here,
        // but at runtime it's only present if the consumer uses Spring Data JPA.
        try {
            Class.forName("org.hibernate.engine.spi.SessionFactoryImplementor")
            registerModule(com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module().apply {
                // Don't serialize uninitialized lazy relations as null —
                // forces explicit initialization instead of silent data loss
                enable(com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS)
            })
        } catch (_: ClassNotFoundException) {
            // Hibernate not present — non-JPA consumers work fine without it
        }
    }
) : KraftJsonSerializer {

    override fun toJson(value: Any?): String = mapper.writeValueAsString(value)

    override fun <T> fromJson(json: String, type: Class<T>): T = mapper.readValue(json, type)

    override fun toMap(value: Any?): Map<String, Any?> =
        mapper.convertValue(
            value,
            mapper.typeFactory.constructMapType(
                Map::class.java,
                String::class.java,
                Any::class.java
            )
        )
}