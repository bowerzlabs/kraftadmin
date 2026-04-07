package com.kraftadmin.persistence.mongo.provider

//class MongoDataProvider<T : Any>(
//    private val mongoTemplate: MongoTemplate,
//    private val entityClass: KClass<T>
//) : KraftDataProvider<T> {
//
//    override fun fetchAll(columns: List<KraftAdminColumn>): List<Map<String, Any?>> {
//        val entities = mongoTemplate.findAll(entityClass.java)
//        return entities.map { entityToMap(it, columns) }
//    }
//
//    override fun fetchById(id: Any, columns: List<KraftAdminColumn>): Map<String, Any?>? {
//        val entity = mongoTemplate.findById(id, entityClass.java)
//        return entity?.let { entityToMap(it, columns) }
//    }
//
//    override fun save(data: Map<String, Any?>): Map<String, Any?> {
//        // MongoTemplate can save a Document (Map) directly if needed,
//        // but for type safety, we usually save the entity class.
//        val entity = mongoTemplate.save(data, mongoTemplate.getCollectionName(entityClass.java))
//        return data // Simplified for demonstration
//    }
//
//    override fun delete(id: Any) {
//        val query = Query(Criteria.where("_id").`is`(id))
//        mongoTemplate.remove(query, entityClass.java)
//    }
//
//    private fun entityToMap(entity: T, columns: List<KraftAdminColumn>): Map<String, Any?> {
//        return columns.associate { col ->
//            val prop = entityClass.memberProperties.find { it.name == col.name }
//            col.name to prop?.getter?.call(entity)
//        }
//    }
//}


