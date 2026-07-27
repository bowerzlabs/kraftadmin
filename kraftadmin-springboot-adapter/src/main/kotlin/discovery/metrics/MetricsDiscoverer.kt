package discovery.metrics

import com.kraftadmin.spi.DiscoveredEntity
import com.kraftadmin.spi.DiscoveredMetric
import persistence.metrics.KraftMetricAnnotations

object MetricDiscoverer {

    fun discover(
        entities: Set<DiscoveredEntity<*>>
    ): List<DiscoveredMetric> {

        return entities.flatMap { discovered ->

            KraftMetricAnnotations
                .findOn(discovered.entityClass)
                .map { annotation ->

                    DiscoveredMetric(
                        entityClass = discovered.entityClass,
                        provider = discovered.provider,
                        metric = annotation
                    )
                }
        }
    }

}