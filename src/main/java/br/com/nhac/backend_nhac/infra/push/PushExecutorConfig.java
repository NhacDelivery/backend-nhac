package br.com.nhac.backend_nhac.infra.push;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class PushExecutorConfig {
    @Bean(name = "pushTaskExecutor")
    public ThreadPoolTaskExecutor pushTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("nhac-push-");
        executor.initialize();
        return executor;
    }
}
