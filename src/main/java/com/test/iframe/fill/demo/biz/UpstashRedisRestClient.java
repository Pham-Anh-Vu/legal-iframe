package com.test.iframe.fill.demo.biz;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Gọi Upstash Redis qua REST API (HTTPS + Bearer), không dùng TCP Redis client.
 *
 * @see <a href="https://upstash.com/docs/redis/features/restapi">Upstash REST API</a>
 */
@Component
public class UpstashRedisRestClient {

	private final Optional<RestClient> restClient;
	private final ObjectMapper objectMapper;

	public UpstashRedisRestClient(
			@Value("${app.upstash.rest-url:}") String restUrl,
			@Value("${app.upstash.token:}") String token,
			ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		if (StringUtils.hasText(restUrl) && StringUtils.hasText(token)) {
			String base = restUrl.endsWith("/") ? restUrl.substring(0, restUrl.length() - 1) : restUrl;
			StringHttpMessageConverter utf8Text = new StringHttpMessageConverter(StandardCharsets.UTF_8);
			utf8Text.setWriteAcceptCharset(false);
			this.restClient = Optional.of(RestClient.builder()
					.baseUrl(base)
					.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.messageConverters(converters -> {
						converters.removeIf(c -> c instanceof StringHttpMessageConverter);
						converters.add(0, utf8Text);
					})
					.build());
		}
		else {
			this.restClient = Optional.empty();
		}
	}

	public boolean isConfigured() {
		return restClient.isPresent();
	}

	/** GET /get/{key} → {@code {"result": "..."} } */
	public Optional<String> get(String redisKey) {
		if (restClient.isEmpty()) {
			return Optional.empty();
		}
		String responseBody = restClient.get().get()
				.uri("/get/{key}", redisKey)
				.retrieve()
				.body(String.class);
		return parseGetResult(responseBody);
	}

	/** POST /set/{key} với body là chuỗi value (phù hợp JSON lớn). */
	public void set(String redisKey, String value) {
		RestClient rc = restClient.orElseThrow(() -> new IllegalStateException(
				"Upstash REST chưa cấu hình: đặt app.upstash.rest-url và biến môi trường UPSTASH_REDIS_REST_TOKEN"));
		String responseBody = rc.post()
				.uri("/get/{key}", redisKey)
				.contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
				.body(value)
				.retrieve()
				.body(String.class);
		assertNoError(responseBody);
	}

	private Optional<String> parseGetResult(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			if (root.has("error")) {
				throw new IllegalStateException("Upstash: " + root.get("error").asText());
			}
			JsonNode r = root.get("result");
			if (r == null || r.isNull()) {
				return Optional.empty();
			}
			return Optional.of(r.asText());
		}
		catch (IllegalStateException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalStateException("Phản hồi Upstash GET không hợp lệ", e);
		}
	}

	private void assertNoError(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			if (root.has("error")) {
				throw new IllegalStateException("Upstash: " + root.get("error").asText());
			}
		}
		catch (IllegalStateException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalStateException("Phản hồi Upstash SET không hợp lệ", e);
		}
	}
}
