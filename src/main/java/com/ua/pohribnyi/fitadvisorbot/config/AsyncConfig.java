package com.ua.pohribnyi.fitadvisorbot.config;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * Async configuration optimized for Gemini API constraints: 
 * - RPM limit: 15 requests/minute 
 * - TPM limit: 250,000 tokens/minute 
 * - Expected load: 15 users/minute, 1000 users/day
 */
@Slf4j
@Configuration
@EnableAsync // Enables Spring's @Async capabilities
public class AsyncConfig {
	
	@Value("${spring.task.execution.pool.ai.core-size}")
	private int aiCoreSize;

	@Value("${spring.task.execution.pool.ai.max-size}")
	private int aiMaxSize;

	@Value("${spring.task.execution.pool.ai.queue-capacity}")
	private int aiQueueCapacity;

	@Value("${spring.task.execution.pool.ai.thread-name}")
	private String aiThreadName;

	@Value("${spring.task.execution.pool.data.core-size}")
	private int dataCoreSize;

	@Value("${spring.task.execution.pool.data.max-size}")
	private int dataMaxSize;

	@Value("${spring.task.execution.pool.data.queue-capacity}")
	private int dataQueueCapacity;

	@Value("${spring.task.execution.pool.data.thread-name}")
	private String dataThreadName;
	
    @Value("${spring.task.execution.pool.core-size:2}")
    private int defaultCoreSize;

    @Value("${spring.task.execution.pool.max-size:4}")
    private int defaultMaxSize;

    @Value("${spring.task.execution.pool.queue-capacity:25}")
    private int defaultQueueCapacity;

    @Value("${spring.task.execution.thread-name-prefix:async-}")
    private String defaultThreadName;
	
	/**
	 * Executor for AI generation tasks (Worker 1: Gemini API calls).
	 * 
	 * Core pool: 5 threads (handles 5 concurrent API calls) Max pool: 10 threads
	 * (burst capacity) Queue: 50 (handles spikes without rejection)
	 * 
	 * Why these numbers? - RPM limit 15 means ~4 calls/second max - 5 threads =
	 * optimal for I/O bound tasks - Queue 50 = buffer for 3+ minutes of requests
	 */
	@Bean(name = "aiGenerationExecutor")
	public Executor aiGenerationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(aiCoreSize);
		executor.setMaxPoolSize(aiMaxSize);
		executor.setQueueCapacity(aiQueueCapacity);
		executor.setThreadNamePrefix(aiThreadName);
		executor.setKeepAliveSeconds(60);

		// CRITICAL FIX: Custom rejection handler
		executor.setRejectedExecutionHandler((runnable, threadPoolExecutor) -> {
			log.error("Thread pool SATURATED! Active={}, Queue={}, Completed={}",
					threadPoolExecutor.getActiveCount(), 
					threadPoolExecutor.getQueue().size(),
					threadPoolExecutor.getCompletedTaskCount());

			throw new RejectedExecutionException(
					"AI generation thread pool is full. System is overloaded. Please try again later.");
		});

		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);

		executor.initialize();

		log.info("✅ AI Generation Executor initialized: core={}, max={}, queue={}", executor.getCorePoolSize(),
				executor.getMaxPoolSize(), executor.getQueueCapacity());

		return executor;
	}

	/**
	 * Executor for data processing tasks (Worker 2: JSON parsing and DB writes).
	 * 
	 * Core pool: 3 threads (sufficient for CPU-bound JSON parsing) Max pool: 5
	 * threads Queue: 100 (larger because processing is faster than generation)
	 */
	@Bean(name = "dataProcessingExecutor")
	public Executor dataProcessingExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(dataCoreSize);
		executor.setMaxPoolSize(dataMaxSize);
		executor.setQueueCapacity(dataQueueCapacity);
		executor.setThreadNamePrefix(dataThreadName);
		executor.setKeepAliveSeconds(60);

		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);

		executor.initialize();

		log.info("Data Processing Executor initialized: core={}, max={}, queue={}", executor.getCorePoolSize(),
				executor.getMaxPoolSize(), executor.getQueueCapacity());

		return executor;
	}

	/**
	 * Fallback executor for other async tasks. Used when no specific executor is
	 * specified.
	 */
	@Bean(name = "taskExecutor")
	public Executor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(defaultCoreSize);
		executor.setMaxPoolSize(defaultMaxSize);
		executor.setQueueCapacity(defaultQueueCapacity);
		executor.setThreadNamePrefix(defaultThreadName);
		executor.initialize();
		return executor;
	}
}
