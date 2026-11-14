package com.nazjara.brewery;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async}, which {@code @ApplicationModuleListener} relies on to run each listener on a separate
 * thread. Without it the {@code @Async} part of the annotation is ignored and listeners run on the publishing thread,
 * still after commit and in their own transaction.
 * <p>
 * The listeners use the {@code applicationTaskExecutor} that Spring Boot auto-configures. Only
 * {@code spring-modulith-events-api} is on the classpath for now: {@code spring-modulith-events-core}, which would
 * enable async on its own, requires an Event Publication Registry. In the microservices version this thread hand-off
 * was a JMS queue between processes.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfiguration {
}
