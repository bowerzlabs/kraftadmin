package com.kraftadmin.api.responses

data class KraftDashboardResponse(
    val title: String,
    val welcomeMessage: String,
    val stats: List<DashboardStat>,
    val features: List<LibraryFeature>,
    val systemStatus: SystemStatus
)

data class DashboardStat(
    val label: String,
    val value: String,
    val icon: String, // SVG path or Lucide name
    val trend: String? = null // e.g., "+12%"
)

data class LibraryFeature(
    val name: String,
    val description: String,
    val status: String, // "Active", "Locked", "Beta"
    val unlockCriteria: String? = null
)

data class SystemStatus(
    val environment: String,
    val databaseType: String,
    val totalEntitiesTracked: Int
)