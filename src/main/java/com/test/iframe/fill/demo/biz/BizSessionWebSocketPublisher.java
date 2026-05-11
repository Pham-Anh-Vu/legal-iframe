package com.test.iframe.fill.demo.biz;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class BizSessionWebSocketPublisher {

	private final SimpMessagingTemplate messagingTemplate;
	private final BizFormRedisService bizFormRedisService;

	public BizSessionWebSocketPublisher(SimpMessagingTemplate messagingTemplate,
			BizFormRedisService bizFormRedisService) {
		this.messagingTemplate = messagingTemplate;
		this.bizFormRedisService = bizFormRedisService;
	}

	/**
	 * Đọc lại JSON trong Redis và gửi tới mọi client đã subscribe theo {@code sessionId}.
	 */
	public void publishDocumentFromRedis(String sessionId) {
		bizFormRedisService.loadRaw(sessionId).ifPresent(json -> messagingTemplate
				.convertAndSend("/topic/biz-session/" + sessionId, json));
	}
}
