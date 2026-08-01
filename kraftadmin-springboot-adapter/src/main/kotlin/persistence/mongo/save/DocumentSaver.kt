package persistence.mongo.save

import com.kraftadmin.logging.KraftAdminLogging
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import persistence.jpa.conversion.FormDataCoercer
import persistence.mongo.conversion.MongoTypeConverter
import persistence.mongo.metadata.MongoEntityMetadata
import kotlin.reflect.KClass

class DocumentSaver<T : Any>(
    private val entityClass: KClass<T>,
    private val mongoTemplate: MongoTemplate,
    private val persistentEntity: MongoPersistentEntity<*>,
    private val idProperty: MongoPersistentProperty?,
    private val entityMetadata: MongoEntityMetadata<T>
) {

    private val logger = KraftAdminLogging.logger(javaClass)

    private val collectionName: String
        get() = persistentEntity.collection

    /**
     * Builds the write-ready field map for the given form payload.
     *
     * Shared by create() and update() so both go through the exact same
     * conversion pipeline (FormDataCoercer -> MongoTypeConverter).
     */
    private fun buildTypedFields(
        data: Map<String, Any?>
    ): Map<String, Any?> {

        val payload =
            data.filterKeys {
                it != "id" &&
                        it != "_id"
            }

        val unwrapped =
            FormDataCoercer.coerce(
                payload
            )

        return MongoTypeConverter.coerceForEntity(
            entityClass.java,
            unwrapped
        )
    }

    /**
     * Inserts a new document.
     *
     * Writes the raw BSON Document directly instead of round-tripping
     * through MappingMongoConverter.read()/save(). That round trip
     * eagerly dereferences @DBRef fields into live entities and then
     * re-serializes them on save — any resolution failure (or a POJO
     * mapping quirk) silently drops the relation. Writing the Document
     * we already built (with real DBRef/ObjectId/Document values from
     * MongoTypeConverter) sidesteps that entirely.
     */
    fun create(
        data: Map<String, Any?>
    ): T? {

        return try {

            val typed =
                buildTypedFields(data)

            val document =
                Document(typed)

            mongoTemplate.insert(
                document,
                collectionName
            )

            val insertedId =
                document["_id"]
                    ?: run {
                        logger.error(
                            "create for {} succeeded but no _id was generated",
                        )
                        return null
                    }

            mongoTemplate.findById(
                insertedId,
                entityClass.java,
                collectionName
            )

        } catch (e: Exception) {

            logger.error(
                "create failed for ${entityClass.simpleName}: ${e.message}",
                e
            )

            null
        }
    }

    /**
     * Updates an existing document in place using $set, rather than
     * reading the whole entity into a POJO and saving it back. This
     * guarantees the exact converted values (DBRefs, embedded Documents,
     * scalars) reach Mongo unchanged — no dereference, no re-mapping.
     */
    fun update(
        id: String,
        data: Map<String, Any?>
    ): T? {

        return try {

            val convertedId =
                entityMetadata.convertId(id)

            val filter =
                Query(
                    Criteria.where("_id")
                        .`is`(convertedId)
                )

            val exists =
                mongoTemplate.exists(
                    filter,
                    entityClass.java,
                    collectionName
                )

            if (!exists) {

                logger.warn(
                    "Could not find {} with ID {}",
                    entityClass.simpleName,
                    id
                )

                return null
            }

            val typed =
                buildTypedFields(data)

            if (typed.isEmpty()) {

                return mongoTemplate.findById(
                    convertedId,
                    entityClass.java,
                    collectionName
                )
            }

            val update =
                Update()

            typed.forEach {
                    (key, value) ->

                update.set(
                    key,
                    value
                )
            }

            mongoTemplate.updateFirst(
                filter,
                update,
                collectionName
            )

            mongoTemplate.findById(
                convertedId,
                entityClass.java,
                collectionName
            )

        } catch (e: Exception) {

            logger.error(
                "update failed for ${entityClass.simpleName} #$id: ${e.message}",
                e
            )

            null
        }
    }
}