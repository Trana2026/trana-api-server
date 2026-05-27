package com.trana.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * @Async 비동기 처리 활성화 + 전용 스레드 풀 설정.
 *
 * 사용처 (W5~):
 * - AiExtractionAsyncProcessor (OpenAI Vision 호출, ~7초)
 *
 * @Async 사용처가 늘어나면 별도 executor 분리 검토 (PDF / 메일 등).
 * 현재는 단일 풀 공유.
 *
 * Bean 이름 "taskExecutor" → @EnableAsync 가 자동 선택
 * (Spring Boot 의 applicationTaskExecutor 보다 이 이름이 우선).
 */
@Configuration
@EnableAsync
class AsyncConfig {
    @Bean(name = ["taskExecutor"])
    fun taskExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = CORE_POOL_SIZE
            maxPoolSize = MAX_POOL_SIZE
            queueCapacity = QUEUE_CAPACITY
            setThreadNamePrefix("trana-async-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(SHUTDOWN_AWAIT_SECONDS)
        }

    companion object {
        private const val CORE_POOL_SIZE = 2
        private const val MAX_POOL_SIZE = 8
        private const val QUEUE_CAPACITY = 50
        private const val SHUTDOWN_AWAIT_SECONDS = 60
    }
}
