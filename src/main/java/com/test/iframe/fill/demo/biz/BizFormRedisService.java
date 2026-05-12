package com.test.iframe.fill.demo.biz;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class BizFormRedisService {

	private static final Logger log = LoggerFactory.getLogger(BizFormRedisService.class);

	public static final String KEY_PREFIX = "biz:session:";

	private final UpstashRedisRestClient upstash;
	private final ObjectMapper objectMapper;
	/** {@code plain} = key trên Upstash đúng bằng session_id; {@code prefixed} = {@code biz:session:}{@code session_id}. */
	private final boolean plainKey;

	public BizFormRedisService(
			UpstashRedisRestClient upstash,
			ObjectMapper objectMapper,
			@Value("${app.upstash.key-style:prefixed}") String keyStyle) {
		this.upstash = upstash;
		this.objectMapper = objectMapper;
		this.plainKey = "plain".equalsIgnoreCase(keyStyle.trim());
	}

	private String redisKey(String sessionId) {
		return plainKey ? sessionId : KEY_PREFIX + sessionId;
	}

	/**
	 * Đọc giá trị Redis theo {@code session_id}, đồng thời log key và payload (hoặc trạng thái rỗng).
	 */
	private String getRawFromRedisLogged(String sessionId) {
		String key = redisKey(sessionId);
		String raw = upstash.get(key).orElse("");
		if (raw.isBlank()) {
			log.info("Redis GET session_id={} redisKey={} -> (không có dữ liệu hoặc chuỗi rỗng)", sessionId, key);
		}
		else {
			log.info("Redis GET session_id={} redisKey={} data={}", sessionId, key, raw);
		}
		return raw;
	}

	/**
	 * Lưu toàn bộ tài liệu đăng ký (các mục giống sample-biz-session.json), không chứa {@code session_id}.
	 */
	public void saveDocument(String sessionId, ObjectNode document) throws JsonProcessingException {
		String json = objectMapper.writeValueAsString(document);
		upstash.set(redisKey(sessionId), json);
	}

	public Optional<String> loadRaw(String sessionId) {
		String raw = getRawFromRedisLogged(sessionId);
		return raw.isBlank() ? Optional.empty() : Optional.of(raw);
	}

	public Optional<Map<String, Object>> loadFlat(String sessionId) {
		String raw = getRawFromRedisLogged(sessionId);
		if (raw.isBlank()) {
			return Optional.empty();
		}
		try {
			JsonNode root = objectMapper.readTree(raw);
			return Optional.of(flatten(root));
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON in Redis for session " + sessionId, e);
		}
	}

	public Optional<JsonNode> loadJson(String sessionId) {
		String raw = getRawFromRedisLogged(sessionId);
		if (raw.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readTree(raw));
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON in Redis for session " + sessionId, e);
		}
	}

	private static Map<String, Object> flatten(JsonNode root) {
		Map<String, Object> out = new LinkedHashMap<>();
		root.fields().forEachRemaining(section -> {
			JsonNode node = section.getValue();
			if (node != null && node.isObject()) {
				node.fields().forEachRemaining(entry -> {
					String k = entry.getKey();
					JsonNode v = entry.getValue();
					if (v == null || v.isNull()) {
						out.put(k, "");
					}
					else if (v.isIntegralNumber()) {
						out.put(k, v.longValue());
					}
					else if (v.isFloatingPointNumber()) {
						out.put(k, v.doubleValue());
					}
					else if (v.isBoolean()) {
						out.put(k, v.booleanValue());
					}
					else {
						out.put(k, v.asText());
					}
				});
			}
		});
		return out;
	}
}
