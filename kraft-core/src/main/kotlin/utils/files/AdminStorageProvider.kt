package com.kraftadmin.utils.files

interface AdminStorageProvider {
    /**
     * Uploads a file and returns the public URL.
     * @param bytes The file content
     * @param fileName The original name (to determine extension)
     * @param context A hint (e.g., "resource-name", "avatar", "cover")
     */
    fun upload(bytes: ByteArray, fileName: String, context: String): String

    fun delete(fileUrl: String)
}