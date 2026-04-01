package com.husky.spring_portfolio.mvc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;

/**
 * Runs non-critical startup seeding off the main thread so {@code SpringApplication.run} can finish
 * and the embedded server can report readiness without waiting on SQLite locks or slow JPA.
 */
@Configuration
public class StartupLifecycleConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StartupLifecycleConfiguration.class);

    /**
     * Dedicated executor for startup seeding (avoids {@code ForkJoinPool.commonPool()} for DB work).
     */
    @Bean(name = "startupTaskExecutor")
    public TaskExecutor startupTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(0);
        ex.setThreadNamePrefix("startup-seed-");
        ex.initialize();
        return ex;
    }

    @Bean
    CommandLineRunner scheduleAsyncStartupSeeding(StartupSeedService seedService,
                                                  @Qualifier("startupTaskExecutor") TaskExecutor startupTaskExecutor) {
        return args -> {
            log.info("Scheduling async database initialization (persons, roles, leaderboard)...");
            CompletableFuture.runAsync(() -> {
                try {
                    seedService.seedAll();
                    log.info("Initialization complete - application ready");
                } catch (Exception e) {
                    log.error("Database initialization failed", e);
                }
            }, startupTaskExecutor);
        };
    }
}
