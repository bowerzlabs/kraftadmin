package persistence.mongo.fetch

import api.utils.ResourceRow
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.spi.KraftAdminColumn
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty
import persistence.mongo.mapper.MongoResourceRowMapper
import persistence.mongo.metadata.MongoEntityMetadata
import kotlin.reflect.KClass

class FetchById<T : Any>(
    private val entityClass: KClass<T>,
    private val mongoTemplate: MongoTemplate,
    private val idProperty: MongoPersistentProperty?,
    private val rowMapper: MongoResourceRowMapper,
    private val entityMetadata: MongoEntityMetadata<T>
) {

    private val logger =
        KraftAdminLogging.logger(
            javaClass
        )

    /**
     * Fetches a Mongo entity and maps it into the complete
     * resource-detail representation.
     *
     * Detail mapping uses MongoConversionMode.DETAIL internally,
     * producing expanded RelatedItem values for relations.
     */
    fun execute(
        id: String,
        columns: List<KraftAdminColumn>
    ): ResourceRow? {

        val entity =
            fetchEntity(
                id = id
            )
                ?: return null

        return rowMapper
            .mapToDetailRow(
                entity = entity,
                columns = columns
            )
    }

    /**
     * Fetches the raw Mongo entity by its string resource ID.
     *
     * The ID is converted through MongoEntityMetadata so that
     * ObjectId, UUID, String, and other supported Mongo ID types
     * can be queried correctly.
     */
    fun fetchEntity(
        id: String
    ): T? {

        return try {

            val convertedId =
                entityMetadata
                    .convertId(
                        id
                    )

            mongoTemplate
                .findById(
                    convertedId,
                    entityClass.java
                )

        } catch (e: Exception) {

            logger.error(
                "Fetch by ID failed for {} with ID {}",
                e
            )

            null
        }
    }
}