package com.web.service.impl;

import com.web.config.AiServiceProperties;
import com.web.dto.CustomerServiceChatRequest;
import com.web.dto.CustomerServiceChatResponse;
import com.web.exception.BusinessException;
import com.web.service.AiCustomerServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
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
    public void streamChat(CustomerServiceChatRequest request, OutputStream outputStream) throws IOException {
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/stream";
        try {
            restTemplate.execute(
                    url,
                    HttpMethod.POST,
                    httpRequest -> {
                        httpRequest.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        httpRequest.getHeaders().setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
                        String body = objectMapper.writeValueAsString(request);
                        StreamUtils.copy(body, StandardCharsets.UTF_8, httpRequest.getBody());
                    },
                    response -> {
                        StreamUtils.copy(response.getBody(), outputStream);
                        outputStream.flush();
                        return null;
                    });
        } catch (RestClientException e) {
            throw new BusinessException("AI_SERVICE_UNAVAILABLE", "智能客服流式服务连接失败，请稍后重试");
        }
    }
}
