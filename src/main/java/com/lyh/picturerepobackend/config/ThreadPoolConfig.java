package com.lyh.picturerepobackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池配置类
 */
@Configuration
public class ThreadPoolConfig {

    @Bean
    public ThreadPoolExecutor customExecutor() {
        // 创建线程工厂
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("custom-thread-" + threadNumber.getAndIncrement());
                return thread;
            }
        };

        // 创建线程池
        return new ThreadPoolExecutor(
                5,                      // 核心线程数
                10,                     // 最大线程数
                60L,                    // 空闲线程存活时间
                TimeUnit.SECONDS,       // 时间单位
                new LinkedBlockingQueue<>(100),  // 工作队列
                threadFactory,          // 线程工厂
                new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
        );
    }
} 