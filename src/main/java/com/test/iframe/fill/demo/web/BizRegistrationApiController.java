package com.test.iframe.fill.demo.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.test.iframe.fill.demo.biz.BizFormRedisService;
import com.test.iframe.fill.demo.biz.BizSessionWebSocketPublisher;

@RestController
@RequestMapping("/api/biz-registration")
public class BizRegistrationApiController {

	private final BizFormRedisService bizFormRedisService;
	private final BizSessionWebSocketPublisher webSocketPublisher;
	private final ObjectMapper objectMapper;

	public BizRegistrationApiController(BizFormRedisService bizFormRedisService,
			BizSessionWebSocketPublisher webSocketPublisher, ObjectMapper objectMapper) {
		this.bizFormRedisService = bizFormRedisService;
		this.webSocketPublisher = webSocketPublisher;
		this.objectMapper = objectMapper;
	}

	/**
	 * Trả về {@link Object} (Map/List/…) để tránh lỗi serialize/kiểu với Jackson 3 trong Spring Web 7.
	 */
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> get(@RequestParam("session_id") String sessionId) {
		return bizFormRedisService.loadRaw(sessionId)
				.map(json -> {
					try {
						return ResponseEntity.ok(objectMapper.readValue(json, Object.class));
					}
					catch (JsonProcessingException e) {
						return ResponseEntity.internalServerError()
								.<Object>body(Map.of("ok", false, "error", e.getMessage()));
					}
				})
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	/**
	 * Body JSON: {@code session_id} (tuỳ chọn) + các khối mẫu. Dùng {@link Map} thay vì {@link JsonNode} trên tham số
	 * {@code @RequestBody} để tương thích Jackson 3 (không deserialize trực tiếp vào {@code JsonNode}).
	 */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> save(@RequestBody(required = false) Map<String, Object> body) {
		try {
			Map<String, Object> payload = body != null ? body : Map.of();
			String sessionId = resolveSessionId(payload);
			Map<String, Object> document = new LinkedHashMap<>(payload);
			document.remove("session_id");
			JsonNode tree = objectMapper.valueToTree(document);
			if (!tree.isObject()) {
				return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Payload phải là object JSON"));
			}
			bizFormRedisService.saveDocument(sessionId, (ObjectNode) tree);
			webSocketPublisher.publishDocumentFromRedis(sessionId);
			return ResponseEntity.ok(Map.of("ok", true, "session_id", sessionId));
		}
		catch (JsonProcessingException e) {
			return ResponseEntity.internalServerError().body(Map.of("ok", false, "error", e.getMessage()));
		}
	}

	private static String resolveSessionId(Map<String, Object> body) {
		Object sid = body.get("session_id");
		if (sid != null) {
			String s = String.valueOf(sid).trim();
			if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
				return s;
			}
		}
		return UUID.randomUUID().toString();
	}
}
