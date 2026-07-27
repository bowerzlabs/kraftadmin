package com.kraftadmin.api.responses

import com.kraftadmin.spi.DataSourceInfo
import com.kraftadmin.spi.MetricResult

data class KraftDashboardResponse(
    val title: String,
    val welcomeMessage: String,
    val stats: List<DashboardStat>,
    val features: List<LibraryFeature>,
    val systemStatus: SystemStatus,
    val metrics: List<MetricResult> = emptyList()
)

data class DashboardStat(
    val label: String,
    val value: String,
    val icon: String, // SVG path or Lucide provider
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
    val isProduction: Boolean,
    val totalEntitiesTracked: Int,
    val dataSources: List<DataSourceInfo>,   // replaces the old single databaseType: String
    val uptimeSeconds: Long,
    val javaVersion: String,
    val appVersion: String?,
)