package persistence.mongo.conversion

enum class MongoConversionMode {

    /**
     * Compact data used by resource tables.
     */
    TABLE,

    /**
     * Expanded data used by resource detail pages.
     */
    DETAIL,

    /**
     * Lightweight id/label values used by forms.
     */
    FORM
}