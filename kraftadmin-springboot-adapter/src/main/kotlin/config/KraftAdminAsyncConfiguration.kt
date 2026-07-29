package config

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * Dedicated executor for KraftAdmin's internal async event dispatch.
 *
 * Deliberately independent of the host application's @EnableAsync /
 * @Async infrastructure -- see SpringKraftEventPublisher for why.
 * A host app can override sizing by defining its own bean named
 * "kraftEventExecutor".
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "kraftadmin",
    name = ["enabled"],
    havingValue = "true"
)
class KraftAdminAsyncConfiguration {

    @Bean("kraftEventExecutor")
    @ConditionalOnMissingBean(name = ["kraftEventExecutor"])
    fun kraftEventExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 8
            queueCapacity = 200
            setThreadNamePrefix("kraft-event-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(10)
            initialize()
        }
    }
}