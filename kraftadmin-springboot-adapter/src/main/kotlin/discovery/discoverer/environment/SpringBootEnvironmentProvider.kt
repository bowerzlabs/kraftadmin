package discovery.discoverer.environment

import com.kraftadmin.spi.DataSourceInfo
import com.kraftadmin.spi.DataSourceKind
import com.kraftadmin.spi.KraftEnvironmentProvider
import com.kraftadmin.spi.RuntimeInfo
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.env.Environment
import java.lang.management.ManagementFactory
import java.time.Instant
import javax.sql.DataSource

class SpringBootEnvironmentProvider(
    private val environment: Environment,
    private val dataSources: ObjectProvider<DataSource>,
    private val mongoDatabases: ObjectProvider<Map<String, Any>>,
    private val appVersion: String
) : KraftEnvironmentProvider {

    private val startedAt: Instant =
        Instant.ofEpochMilli(
            ManagementFactory.getRuntimeMXBean().startTime
        )

    override fun getAuthMode(): String =
        if (isSpringSecurityActive()) "bridge" else "standalone"

    override fun getShouldShowLogout(): Boolean =
        !isSpringSecurityActive()

    override fun getEnvironmentName(): String =
        environment.activeProfiles
            .takeIf { it.isNotEmpty() }
            ?.joinToString(",")
            ?: "default"

    override fun isProduction(): Boolean =
        environment.activeProfiles.any {
            it.equals("prod", ignoreCase = true) ||
                    it.equals("production", ignoreCase = true)
        }

    override fun getDataSources(): List<DataSourceInfo> {
        /*
         * Important:
         *
         * Do not resolve DataSource beans during application startup.
         * ObjectProvider keeps this lazy and avoids creating a dependency
         * cycle with other auto-configured beans.
         */
        val relational = dataSources
            .iterator()
            .asSequence()
            .mapNotNull { dataSource ->
                describeJdbc(
                    name = resolveDataSourceName(dataSource),
                    ds = dataSource
                )
            }
            .toList()

        val documentStores = mongoDatabases
            .getIfAvailable { emptyMap() }
            .mapNotNull { (name, database) ->
                describeMongo(name, database)
            }

        return relational + documentStores
    }

    private fun resolveDataSourceName(dataSource: DataSource): String {
        return when (dataSource) {
            is HikariDataSource -> {
                dataSource.poolName
                    ?: "datasource"
            }

            else -> {
                dataSource.javaClass.simpleName
            }
        }
    }

    private fun describeJdbc(
        name: String,
        ds: DataSource
    ): DataSourceInfo? {
        return try {
            ds.connection.use { connection ->

                val meta = connection.metaData

                val hikari = ds as? HikariDataSource
                val pool = hikari?.hikariPoolMXBean

                DataSourceInfo(
                    name = name,
                    kind = DataSourceKind.RELATIONAL,
                    productName = meta.databaseProductName,
                    productVersion = meta.databaseProductVersion,
                    driverOrClientName = meta.driverName,
                    connectionString = redact(meta.url),

                    poolType = hikari?.let { "HikariCP" },

                    activeConnections =
                        pool?.activeConnections,

                    idleConnections =
                        pool?.idleConnections,

                    maxPoolSize =
                        hikari?.maximumPoolSize,

                    reachable = true
                )
            }

        } catch (e: Exception) {

            DataSourceInfo(
                name = name,
                kind = DataSourceKind.RELATIONAL,
                productName = "Unknown",
                productVersion = null,
                driverOrClientName = null,
                connectionString = "unreachable",
                poolType = null,
                activeConnections = null,
                idleConnections = null,
                maxPoolSize = null,
                reachable = false
            )
        }
    }

    /**
     * MongoDB is intentionally handled reflectively.
     *
     * This keeps KraftAdmin free from a hard compile-time MongoDB dependency.
     */
    private fun describeMongo(
        name: String,
        db: Any
    ): DataSourceInfo? {

        return try {

            val getName =
                db.javaClass.getMethod("getName")

            val databaseName =
                getName.invoke(db) as? String
                    ?: "unknown"

            DataSourceInfo(
                name = name,
                kind = DataSourceKind.DOCUMENT,
                productName = "MongoDB",
                productVersion = null,
                driverOrClientName = "MongoDB Java Driver",
                connectionString = redact(databaseName),
                poolType = "Mongo connection pool",
                activeConnections = null,
                idleConnections = null,
                maxPoolSize = null,
                reachable = true,
                extra = mapOf(
                    "database" to databaseName
                )
            )

        } catch (_: Exception) {
            null
        }
    }

    private fun redact(url: String): String {
        return try {
            url
                .replace(
                    Regex(
                        "password=[^&;]*",
                        RegexOption.IGNORE_CASE
                    ),
                    "password=***"
                )
                .replace(
                    Regex(
                        "://[^:/@]+:[^@/]+@"
                    ),
                    "://***:***@"
                )
        } catch (_: Exception) {
            "***redacted***"
        }
    }

    override fun getRuntimeInfo(): RuntimeInfo {

        val now = Instant.now()

        return RuntimeInfo(
            activeProfiles = environment.activeProfiles.toList(),
            javaVersion =
                System.getProperty("java.version")
                    ?: "unknown",
            startedAt = startedAt,
            uptimeSeconds =
                now.epochSecond - startedAt.epochSecond,
            appVersion = appVersion
        )
    }

    private fun isSpringSecurityActive(): Boolean {
        return try {
            Class.forName(
                "org.springframework.security.web.SecurityFilterChain",
                false,
                javaClass.classLoader
            )

            true

        } catch (_: ClassNotFoundException) {
            false
        }
    }
}