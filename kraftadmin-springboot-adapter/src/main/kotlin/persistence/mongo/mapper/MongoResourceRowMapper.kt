package persistence.mongo.mapper

import api.utils.ResourceRow
import com.kraftadmin.enums.FormInputType
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.spi.KraftAdminColumn
import events.SpringActionRegistry
import org.springframework.context.ApplicationContext
import persistence.mongo.conversion.MongoConversionMode
import persistence.mongo.conversion.MongoValueConverter
import kotlin.reflect.KClass

class MongoResourceRowMapper(
    private val entityClass: KClass<*>,
    private val applicationContext: ApplicationContext
) {

    private val logger =
        KraftAdminLogging.logger(
            javaClass
        )

    private val springActionRegistry =
        applicationContext.getBean(
            SpringActionRegistry::class.java
        )

    /**
     * Maps a Mongo entity into a compact resource table row.
     *
     * Table behavior:
     *
     * - Scalar fields are included.
     * - Single relations may be included.
     * - Multi-relations and collections are excluded because
     *   resolving Mongo references for every row can cause
     *   expensive N+1 queries.
     */
    fun mapToRow(
        entity: Any,
        columns: List<KraftAdminColumn>
    ): ResourceRow {

        val id =
            MongoValueConverter
                .extractIdentifier(
                    entity
                )
                ?.toString()
                ?: ""

        val timestampFields =
            setOf(
                "createdAt",
                "updatedAt"
            )

        val selectedColumns =
            columns
                .asSequence()
                .filter {
                        column ->

                    column.name !in
                            timestampFields &&
                            shouldIncludeInTable(
                                column
                            )
                }
                .take(8)
                .map {
                        column ->

                    column.name
                }
                .toList()

        val allowedFields =
            (
                    selectedColumns +
                            timestampFields
                    )
                .distinct()

        val values =
            MongoValueConverter
                .mapObjectToValues(
                    entity = entity,
                    fieldsToRead =
                        allowedFields,
                    mode =
                        MongoConversionMode.TABLE
                )

        return ResourceRow(
            id = id,
            values = values,
            metadata =
                buildMetadata(
                    entity = entity
                ),
            relatedResources = null
        )
    }

    /**
     * Determines whether a column should be included in
     * a Mongo resource table.
     *
     * Single relations remain available in table rows.
     *
     * Multi-relations and ordinary collections are excluded
     * because they may trigger many Mongo reference lookups.
     */
    private fun shouldIncludeInTable(
        column: KraftAdminColumn
    ): Boolean {

        return when (
            column.type
        ) {

            FormInputType.MULTI_RELATION,
            FormInputType.COLLECTION ->
                false

            else ->
                true
        }
    }

    /**
     * Maps the complete Mongo entity for a resource
     * detail page.
     *
     * DETAIL mode expands relations into RelatedItem values.
     */
    fun mapToDetailRow(
        entity: Any,
        columns: List<KraftAdminColumn>
    ): ResourceRow {

        val id =
            MongoValueConverter
                .extractIdentifier(
                    entity
                )
                ?.toString()
                ?: ""

        val values =
            MongoValueConverter
                .mapObjectToValues(
                    entity = entity,
                    mode =
                        MongoConversionMode.DETAIL
                )

        return ResourceRow(
            id = id,
            values = values,
            metadata =
                buildMetadata(
                    entity = entity
                ),
            customActions =
                springActionRegistry
                    .getResourceActions(
                        entity::class
                    ),
            relatedResources = null
        )
    }

    /**
     * Converts an entity into form-compatible data.
     *
     * FORM mode converts relations into ObjectResponse values.
     */
    fun mapEntityToData(
        entity: Any?
    ): Map<String, Any?> {

        return MongoValueConverter
            .mapObjectToValues(
                entity = entity,
                mode =
                    MongoConversionMode.FORM
            )
    }

    private fun buildMetadata(
        entity: Any
    ): Map<String, Any?> {

        return mapOf(
            "canEdit" to true,
            "canDelete" to true,
            "cssClass" to null
        )
    }
}