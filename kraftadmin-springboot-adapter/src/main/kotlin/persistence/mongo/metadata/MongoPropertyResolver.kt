package persistence.mongo.metadata

import org.bson.types.ObjectId
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.time.temporal.Temporal
import java.util.Date
import kotlin.reflect.KClass

/**
 * Mongo counterpart to persistence.jpa.metadata.PropertyResolver.
 *
 * Used by MongoRelatedResourceFetcher to decide which fields on a
 * *related* document are safe to surface as lightweight "values" —
 * simple scalars only, never nested relations or collections, so a
 * detail-view fetch never cascades into a deep resolve.
 */
object MongoPropertyResolver {

    fun shouldSkip(field: Field): Boolean {
        val name = field.name
        return Modifier.isStatic(field.modifiers) ||
                Modifier.isTransient(field.modifiers) ||
                field.isSynthetic ||
                name == "\$stable" ||
                name.startsWith("this$")
    }

    fun isSimpleType(kClass: KClass<*>): Boolean {
        val javaType = kClass.java
        return javaType == String::class.java ||
                javaType == Boolean::class.java || javaType == java.lang.Boolean::class.java ||
                javaType == Char::class.java || javaType == Character::class.java ||
                Number::class.java.isAssignableFrom(javaType) ||
                javaType.isPrimitive ||
                javaType.isEnum ||
                ObjectId::class.java.isAssignableFrom(javaType) ||
                Date::class.java.isAssignableFrom(javaType) ||
                Temporal::class.java.isAssignableFrom(javaType)
    }
}