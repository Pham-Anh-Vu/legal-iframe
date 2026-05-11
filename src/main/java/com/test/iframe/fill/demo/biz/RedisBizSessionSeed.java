package com.test.iframe.fill.demo.biz;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RedisBizSessionSeed implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(RedisBizSessionSeed.class);

	private final UpstashRedisRestClient upstash;
	private final ResourceLoader resourceLoader;
	private final ObjectMapper objectMapper;

	@Value("${app.redis.seed-on-startup:false}")
	private boolean seedOnStartup;

	@Value("${app.redis.demo-session-id:demo-session-001}")
	private String demoSessionId;

	@Value("${app.upstash.key-style:prefixed}")
	private String keyStyle;

	public RedisBizSessionSeed(UpstashRedisRestClient upstash, ResourceLoader resourceLoader,
			ObjectMapper objectMapper) {
		this.upstash = upstash;
		this.resourceLoader = resourceLoader;
		this.objectMapper = objectMapper;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!seedOnStartup) {
			return;
		}
		if (!upstash.isConfigured()) {
			log.info("Seed bỏ qua: Upstash REST chưa cấu hình (app.upstash.rest-url / UPSTASH_REDIS_REST_TOKEN)");
			return;
		}
		try {
			Resource res = resourceLoader.getResource("classpath:sample-biz-session.json");
			String json = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			objectMapper.readTree(json);
			String key = "plain".equalsIgnoreCase(keyStyle.trim()) ? demoSessionId
					: BizFormRedisService.KEY_PREFIX + demoSessionId;
			upstash.set(key, json);
			log.info("Đã seed Upstash key {} từ sample-biz-session.json", key);
		}
		catch (Exception e) {
			log.warn("Redis seed skipped: {}", e.getMessage());
		}
	}
}
