package util

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import json.KraftJsonSerializer

class JacksonKraftJsonSerializer(
    private val mapper: ObjectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerModule(kotlinModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        // Hibernate is optional and consumer-provided — never relocated
        try {
            Class.forName("com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module")
            val hibernateModule = Class.forName("com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module")
                .getDeclaredConstructor()
                .newInstance()
            // Configure via reflection to avoid compile-time relocation
            val featureClass = Class.forName("com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module\$Feature")
            val feature = featureClass.enumConstants
                .first { (it as Enum<*>).name == "SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS" }
            hibernateModule.javaClass
                .getMethod("enable", featureClass)
                .invoke(hibernateModule, feature)
            registerModule(hibernateModule as Module)
        } catch (_: ClassNotFoundException) {
            // Hibernate not on classpath — fine
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
    override fun toPrettyJson(value: Any?): String =
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
}