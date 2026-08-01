package discovery.descriptors.column.mongo

import com.kraftadmin.annotations.KraftAdminField
import com.kraftadmin.annotations.KraftAdminLookup
import com.kraftadmin.ui_descriptors.LookupDescriptor
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.DocumentReference
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

class MongoLookupResolver {

    fun buildLookup(
        property: KProperty1<out Any, *>,
        targetDocumentClass: KClass<*>?
    ): LookupDescriptor? {

        if (targetDocumentClass == null) {
            return null
        }

        val javaField = property.javaField

        val isMongoReference =
            javaField?.isAnnotationPresent(DBRef::class.java) == true ||
                    javaField?.isAnnotationPresent(DocumentReference::class.java) == true

        if (!isMongoReference) {
            return null
        }

        val fieldAnnotation =
            javaField?.getAnnotation(
                KraftAdminLookup::class.java
            )

        val documentAnnotation =
            targetDocumentClass.java.getAnnotation(
                KraftAdminLookup::class.java
            )

        val lookupAnnotation =
            fieldAnnotation ?: documentAnnotation

        val displayField =
            lookupAnnotation
                ?.displayField
                ?.takeIf { it.isNotBlank() }
                ?: discoverDefaultDisplayField(
                    targetDocumentClass
                )

        val lookupKey =
            lookupAnnotation
                ?.lookupKey
                ?.takeIf { it.isNotBlank() }
                ?: discoverIdField(
                    targetDocumentClass
                )

        return LookupDescriptor(
            targetEntity =
                targetDocumentClass.simpleName
                    ?: "Unknown",

            searchableFields =
                discoverSearchableFields(
                    targetDocumentClass
                ),

            displayField = displayField,

            lookupKey = lookupKey
        )
    }

    /**
     * Finds fields suitable for searching in a Mongo lookup.
     *
     * Priority:
     *
     * 1. Fields explicitly marked searchable
     * 2. String properties
     * 3. Display field
     */
    private fun discoverSearchableFields(
        targetClass: KClass<*>
    ): List<String> {

        val properties =
            targetClass.memberProperties

        val explicitlySearchable =
            properties
                .filter { property ->

                    property.javaField
                        ?.getAnnotation(
                            KraftAdminField::class.java
                        )
                        ?.searchable == true
                }
                .map { it.name }

        if (explicitlySearchable.isNotEmpty()) {
            return explicitlySearchable
        }

        val stringFields =
            properties
                .filter { property ->

                    property.returnType.classifier ==
                            String::class
                }
                .filterNot { property ->

                    property.name.equals(
                        "id",
                        ignoreCase = true
                    ) ||
                            property.name == "_id"
                }
                .map { it.name }

        if (stringFields.isNotEmpty()) {
            return stringFields
        }

        return listOf(
            discoverDefaultDisplayField(
                targetClass
            )
        )
    }

    /**
     * Determines the field displayed by the lookup.
     *
     * Priority:
     *
     * 1. @KraftAdminField(displayField = true)
     * 2. displayName
     * 3. title
     * 4. name
     * 5. label
     * 6. username
     * 7. email
     * 8. First String property
     * 9. Mongo ID
     */
    private fun discoverDefaultDisplayField(
        targetClass: KClass<*>
    ): String {

        val properties =
            targetClass.memberProperties

        val manuallyMarked =
            properties.firstOrNull {
                it.javaField
                    ?.getAnnotation(
                        KraftAdminField::class.java
                    )
                    ?.displayField == true
            }

        if (manuallyMarked != null) {
            return manuallyMarked.name
        }

        val preferredNames =
            listOf(
                "displayName",
                "title",
                "name",
                "label",
                "username",
                "email",
                "provider"
            )

        for (preferredName in preferredNames) {

            val property =
                properties.firstOrNull {

                    it.name.equals(
                        preferredName,
                        ignoreCase = true
                    )
                }

            if (property != null) {
                return property.name
            }
        }

        val firstString =
            properties.firstOrNull {

                it.returnType.classifier ==
                        String::class &&

                        !it.name.endsWith(
                            "Id",
                            ignoreCase = true
                        )
            }

        return firstString?.name
            ?: discoverIdField(targetClass)
    }

    /**
     * Finds the Mongo identifier property.
     *
     * Supports:
     *
     * - Spring Data @Id
     * - id
     * - _id
     */
    private fun discoverIdField(
        targetClass: KClass<*>
    ): String {

        val idProperty =
            targetClass.memberProperties
                .firstOrNull {

                    it.findAnnotation<Id>() != null
                }

        if (idProperty != null) {
            return idProperty.name
        }

        val conventionalId =
            targetClass.memberProperties
                .firstOrNull {

                    it.name.equals(
                        "id",
                        ignoreCase = true
                    ) ||
                            it.name == "_id"
                }

        return conventionalId?.name ?: "id"
    }
}