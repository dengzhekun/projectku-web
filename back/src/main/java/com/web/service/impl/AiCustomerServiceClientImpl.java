package com.web.service.impl;

import com.web.config.AiServiceProperties;
import com.web.dto.CustomerServiceChatRequest;
import com.web.dto.CustomerServiceChatResponse;
import com.web.exception.BusinessException;
import com.web.service.AiCustomerServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AiCustomerServiceClientImpl implements AiCustomerServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AiServiceProperties properties;

    public AiCustomerServiceClientImpl(AiServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = Math.max(properties.getTimeoutSeconds(), 1) * 1000;
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper();
        this.properties = properties;
    }

    @Override
    public CustomerServiceChatResponse chat(CustomerServiceChatRequest request) {
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/chat";
        try {
            CustomerServiceChatResponse response =
                    restTemplate.postForObject(url, request, CustomerServiceChatResponse.class);
            if (response == null || response.getAnswer() == null || response.getAnswer().trim().isEmpty()) {
                throw new BusinessException("AI_SERVICE_UNAVAILABLE", "智能客服暂时无法回答，请稍后重试");
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException("AI_SERVICE_UNAVAILABLE", "智能客服服务连接失败，请稍后重试");
        } catch (Exception e) {
            throw new BusinessException("AI_SERVICE_UNAVAILABLE", "智能客服响应异常，请稍后重试");
        }
    }

    @Override
    public CustomerServiceChatResponse streamChat(CustomerServiceChatRequest request, OutputStream outputStream) throws IOException {
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/stream";
        try {
            CustomerServiceChatResponse finalReply = restTemplate.execute(
                    url,
                    HttpMethod.POST,
                    httpRequest -> {
                        httpRequest.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        httpRequest.getHeaders().setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
                        String body = objectMapper.writeValueAsString(request);
                        StreamUtils.copy(body, StandardCharsets.UTF_8, httpRequest.getBody());
                    },
                    response -> {
                        CustomerServiceChatResponse capturedFinalReply = null;
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8));
                        StringBuilder eventData = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            StreamUtils.copy(line + "\n", StandardCharsets.UTF_8, outputStream);
                            outputStream.flush();
                            if (line.startsWith("data:")) {
                                if (eventData.length() > 0) {
                                    eventData.append('\n');
                                }
                                eventData.append(line.substring(5).trim());
                            } else if (line.isBlank() && eventData.length() > 0) {
                                CustomerServiceChatResponse parsed = parseFinalStreamReply(eventData.toString());
                                if (parsed != null) {
                                    capturedFinalReply = parsed;
                                }
                                eventData.setLength(0);
                            }
                        }
                        if (eventData.length() > 0) {
                            CustomerServiceChatResponse parsed = parseFinalStreamReply(eventData.toString());
                            if (parsed != null) {
                                capturedFinalReply = parsed;
                            }
                        }
                        outputStream.flush();
                        return capturedFinalReply;
                    });
            return finalReply;
        } catch (RestClientException e) {
            throw new BusinessException("AI_SERVICE_UNAVAILABLE", "智能客服流式服务连接失败，请稍后重试");
        }
    }

    private CustomerServiceChatResponse parseFinalStreamReply(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            if (!"final".equals(root.path("type").asText())) {
                return null;
            }
            JsonNode reply = root.path("reply");
            if (reply.isMissingNode() || reply.isNull()) {
                return null;
            }
            return objectMapper.treeToValue(reply, CustomerServiceChatResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}
