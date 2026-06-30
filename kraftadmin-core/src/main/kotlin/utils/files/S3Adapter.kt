package com.kraftadmin.utils.files

import org.slf4j.LoggerFactory

class S3Adapter(
    private val s3Client: Any, // software.amazon.awssdk.services.s3.S3Client passed as Any
    private val bucketName: String
) : AdminStorageProvider {

    private val logger = LoggerFactory.getLogger(S3Adapter::class.java)

    override fun upload(bytes: ByteArray, fileName: String, context: String): String {
        val extension = fileName.substringAfterLast(".", "bin")
        val uniqueName = "$context/${java.util.UUID.randomUUID()}.$extension"

        try {
            // Get the classes using the parent Application's ClassLoader
            val classLoader = s3Client.javaClass.classLoader
            val putObjectRequestClass = Class.forName("software.amazon.awssdk.services.s3.model.PutObjectRequest", true, classLoader)
            val requestBodyClass = Class.forName("software.amazon.awssdk.core.sync.RequestBody", true, classLoader)

            // Build PutObjectRequest using its builder pattern reflectively
            val builderInstance = putObjectRequestClass.getMethod("builder").invoke(null)
            builderInstance.javaClass.getMethod("bucket", String::class.java).invoke(builderInstance, bucketName)
            builderInstance.javaClass.getMethod("key", String::class.java).invoke(builderInstance, uniqueName)
            val putObjectRequest = builderInstance.javaClass.getMethod("build").invoke(builderInstance)
            
            // Convert raw bytes to RequestBody: RequestBody.fromBytes(bytes)
            val requestBody = requestBodyClass.getMethod("fromBytes", ByteArray::class.java).invoke(null, bytes)

            // Invoke s3Client.putObject(putObjectRequest, requestBody)
            s3Client.javaClass.getMethod(
                "putObject",
                putObjectRequestClass,
                requestBodyClass
            ).invoke(s3Client, putObjectRequest, requestBody)

            return "https://$bucketName.s3.amazonaws.com/$uniqueName"
        } catch (e: Exception) {
            logger.error("AWS S3 dynamic reflection upload failed", e)
            throw RuntimeException("Failed to upload file to AWS S3: ${e.message}", e)
        }
    }

    override fun delete(fileUrl: String) {
        try {
            val s3HostSuffix = "$bucketName.s3.amazonaws.com/"
            if (!fileUrl.contains(s3HostSuffix)) return
            val key = fileUrl.substringAfter(s3HostSuffix)

            val classLoader = s3Client.javaClass.classLoader
            val deleteObjectRequestClass = Class.forName("software.amazon.awssdk.services.s3.model.DeleteObjectRequest", true, classLoader)

            // Build DeleteObjectRequest reflectively
            val builderInstance = deleteObjectRequestClass.getMethod("builder").invoke(null)
            builderInstance.javaClass.getMethod("bucket", String::class.java).invoke(builderInstance, bucketName)
            builderInstance.javaClass.getMethod("key", String::class.java).invoke(builderInstance, key)
            val deleteObjectRequest = builderInstance.javaClass.getMethod("build").invoke(builderInstance)

            // Invoke s3Client.deleteObject(deleteObjectRequest)
            s3Client.javaClass.getMethod("deleteObject", deleteObjectRequestClass).invoke(s3Client, deleteObjectRequest)
            logger.info("Successfully deleted file from S3 bucket: {}", key)
        } catch (e: Exception) {
            logger.error("AWS S3 dynamic file removal failed for path: $fileUrl", e)
        }
    }

    /**
     * Checks if the asset string explicitly targets this unique S3 bucket endpoint zone.
     */
    override fun contains(fileUrl: String): Boolean {
        return fileUrl.contains("$bucketName.s3.", ignoreCase = true) || fileUrl.contains("//$bucketName.s3")
    }
}