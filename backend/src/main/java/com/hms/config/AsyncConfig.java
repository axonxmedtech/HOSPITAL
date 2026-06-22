package com.hms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
    // Spring Boot's default SimpleAsyncTaskExecutor handles @Async methods.
    // @EnableScheduling enables @Scheduled — WhatsApp reminder and retry schedulers use it.
}
