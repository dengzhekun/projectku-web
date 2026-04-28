package com.web.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.exception.BusinessException;
import com.web.service.KhojChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class KhojChatClientImpl implements KhojChatClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String chatUrl;

    public KhojChatClientImpl(
            ObjectMapper objectMapper,
            @Value("${khoj.base-url:http://127.0.0.1:42110}") String baseUrl) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.chatUrl = baseUrl.replaceAll("/+$", "") + "/api/chat";
    }

    @Override
    public String chat(String prompt, String conversationId) {
        Map<String, Object> body = new HashMap<>();
        body.put("q", prompt);
        body.put("stream", false);
        body.put("n", 5);
        body.put("create_new", conversationId == null || conversationId.isBlank());
        if (conversationId != null && !conversationId.isBlank()) {
            body.put("conversation_id", conversationId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            String raw = restTemplate.postForObject(chatUrl, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(raw == null ? "{}" : raw);
            String response = root.path("response").asText("").trim();
            if (response.isEmpty()) {
                throw new BusinessException("AI_SERVICE_UNAVAILABLE", "在线客服暂时无法回答，请稍后重试");
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException("AI_SERVICE_UNAVAILABLE", "在线客服连接失败，请稍后重试");
        } catch (Exception e) {
            throw new BusinessException("AI_SERVICE_UNAVAILABLE", "在线客服响应解析失败，请稍后重试");
        }
    }
}
