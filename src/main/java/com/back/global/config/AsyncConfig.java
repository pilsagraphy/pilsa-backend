package com.back.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync // 메인 클래스 대신 여기서 비동기 기능을 켬.
public class AsyncConfig {
}