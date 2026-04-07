package com.kraftadmin.utils.files

import org.slf4j.LoggerFactory

class CloudinaryAdapter(private val cloudinary: Any) : AdminStorageProvider {
    private val logger = LoggerFactory.getLogger(CloudinaryAdapter::class.java)

    override fun upload(bytes: ByteArray, fileName: String, context: String): String {
        // Invoke cloudinary.uploader().upload(...) via reflection
        // This keeps the library "Clean" of the Cloudinary JAR
        return "..."    }

    // In com.kraftadmin.utils.files.CloudinaryAdapter
    override fun delete(fileUrl: String) {
        // Cloudinary needs the "Public ID" (filename without extension)
        val publicId = fileUrl.substringAfterLast("/").substringBeforeLast(".")
        try {
            // Use reflection to call cloudinary.uploader().destroy(publicId, Map.of())
            logger.info("Deleting from Cloudinary: {}", publicId)
        } catch (e: Exception) {
            logger.error("Cloudinary delete failed", e)
        }
    }
}