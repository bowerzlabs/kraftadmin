package persistence.metrics

import com.kraftadmin.enums.ProviderType
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.spi.DiscoveredMetric
import com.kraftadmin.spi.KraftMetricProvider
import com.kraftadmin.spi.MetricResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
@ConditionalOnProperty(
    prefix = "kraftadmin",
    name = ["enabled"],
    havingValue = "true"
)
class KraftMetricService(
    metricProviders: List<KraftMetricProvider>
) {

    private val logger =
        KraftAdminLogging.logger(javaClass)

    private val providersByType:
            Map<ProviderType, KraftMetricProvider> =
        buildMap {

            for (provider in metricProviders) {
                for (type in knownProviderTypes(provider)) {
                    put(type, provider)
                }
            }
        }

    private fun knownProviderTypes(
        provider: KraftMetricProvider
    ): List<ProviderType> =
        ProviderType.entries.filter { provider.supports(it) }

    fun compute(
        discoveredMetrics: List<DiscoveredMetric>
    ): List<MetricResult> {

        if (discoveredMetrics.isEmpty()) {
            return emptyList()
        }

        val futures =
            discoveredMetrics
                .mapNotNull { discoveredMetric ->

                    val provider =
                        providersByType[discoveredMetric.provider]
                            ?: return@mapNotNull null

                    discoveredMetric to provider
                }
                .map { (discoveredMetric, provider) ->

                    CompletableFuture.supplyAsync {

                        runCatching {
                            provider.computeMetric(
                                discoveredMetric.entityClass,
                                discoveredMetric.metric
                            )
                        }
                            .onFailure { exception ->
                                logger.warn(
                                    "Metric '{}' failed to compute: {}",
                                    discoveredMetric.metric.name,
                                    exception.message
                                )
                            }
                            .getOrNull()
                    }
                }

        return futures
            .mapNotNull { it.join() }
    }
}