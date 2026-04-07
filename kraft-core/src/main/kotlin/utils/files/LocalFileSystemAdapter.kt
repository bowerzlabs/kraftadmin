package com.kraftadmin.utils.files

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.*

class LocalFileSystemAdapter(
    private val uploadDir: String = "uploads/admin",
    private val publicBaseDir: String = "public",
) : AdminStorageProvider {

    private val logger: Logger = LoggerFactory.getLogger(LocalFileSystemAdapter::class.java)

    init {
        // Ensure the directory exists immediately on startup
        logger.info("KraftAdmin: No cloud storage detected. Falling back to local: {}", uploadDir)
        File(uploadDir).mkdirs()
    }

    override fun upload(bytes: ByteArray, fileName: String, context: String): String {
        val extension = fileName.substringAfterLast(".", "bin")
        val uniqueName = "${UUID.randomUUID()}.$extension"
        val targetFile = File(uploadDir, uniqueName)

        Files.write(targetFile.toPath(), bytes)

        return "/admin/files/$uniqueName"
    }

    override fun delete(fileUrl: String) {
        try {
            // Extract the filename from the URL
            // e.g., /admin/files/abc-123.jpg -> abc-123.jpg
            val fileName = fileUrl.substringAfterLast("/")
            val file = File(uploadDir, fileName)

            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) logger.info("Successfully deleted old file: {}", fileName)
            }
        } catch (e: Exception) {
            logger.error("Failed to delete file at $fileUrl", e)
        }
    }
}