package config

import analytics.TelemetryWriter
import util.DefaultPulseContextProvider
import interceptors.PulseContextProvider
import interceptors.QueryPulseInterceptor
import jakarta.annotation.PostConstruct
import model.PulseContext
import net.ttddyy.dsproxy.ExecutionInfo
import net.ttddyy.dsproxy.QueryInfo
import net.ttddyy.dsproxy.listener.QueryExecutionListener
import net.ttddyy.dsproxy.support.ProxyDataSource
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import persistence.jpa.SqlQueryEventBuilder
import util.JpaPulseQueryListener
import java.util.concurrent.Executor
import javax.sql.DataSource

// JpaPulseAutoconfiguration becomes purely the DataSource-wrapping mechanism,
// delegating the actual listener implementation to JpaPulseQueryListener
@AutoConfiguration
@ConditionalOnExpression("\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}")
class JpaPulseAutoconfiguration(
    private val beanFactory: ListableBeanFactory
) : BeanPostProcessor, QueryExecutionListener {

    // Lazily resolve the listener bean — avoids circular bean creation issues
//    private val listener by lazy { beanFactory.getBean(JpaPulseQueryListener::class.java) }
    private val builder = SqlQueryEventBuilder()
    private val interceptor by lazy { beanFactory.getBean(QueryPulseInterceptor::class.java) }
    private val contextProvider by lazy { beanFactory.getBean(PulseContextProvider::class.java) }

    @PostConstruct
    fun init() {
        println(" KraftPulse: SQL Sniffer Initialized")
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean is DataSource && bean !is ProxyDataSource) {
            println("KraftPulse: Wrapping DataSource [$beanName]")
            return ProxyDataSourceBuilder.create(bean)
                .name(beanName)
                .listener(this) // ✅ delegate to JpaPulseQueryListener
                .build()
        }
        return bean
    }

    @Bean
    @ConditionalOnMissingBean
    fun pulseContextProvider(): PulseContextProvider = DefaultPulseContextProvider()

    @Bean
    @ConditionalOnMissingBean
    fun queryPulseInterceptor(telemetryWriter: TelemetryWriter): QueryPulseInterceptor =
        AsyncQueryPulseInterceptor(telemetryWriter)

    @Bean(name = ["pulseTaskExecutor"])
    fun pulseTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 10
        executor.setQueueCapacity(1000)
        executor.setThreadNamePrefix("KraftPulse-DB-")
        executor.initialize()
        return executor
    }

        override fun afterQuery(execInfo: ExecutionInfo?, queryInfoList: List<QueryInfo?>?) {
        if (execInfo == null || queryInfoList == null) return
        try {
            val currentContext = contextProvider.currentContext() ?: PulseContext.SYSTEM_DEFAULT
            val events = builder.buildEvents(execInfo, queryInfoList.filterNotNull(), "SQL-Source")
            events.forEach { interceptor.onQuery(currentContext, it) }
        } catch (e: Exception) {
            println("KraftPulse Error: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun beforeQuery(execInfo: ExecutionInfo?, queryInfoList: List<QueryInfo?>?) {
        // No-op
    }


}