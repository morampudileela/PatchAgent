package com.patchagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Shared executor for:
     *  - background job threads (run_job)
     *  - SSE queue-drain threads (one per active stream)
     *  - batch parallel SSH threads (within a job)
     *  - pre-flight status-check threads (parallel SSH)
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("patch-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}
