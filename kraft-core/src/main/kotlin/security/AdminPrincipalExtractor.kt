package com.kraftadmin.security

interface AdminPrincipalExtractor {
    fun extractName(raw: Any): String?
    fun extractAvatar(raw: Any): String?
}