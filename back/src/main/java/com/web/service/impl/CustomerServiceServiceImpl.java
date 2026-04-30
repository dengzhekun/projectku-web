package com.web.service.impl;

import com.web.dto.CustomerServiceChatRequest;
import com.web.dto.CustomerServiceChatResponse;
import com.web.dto.CustomerServiceCitation;
import com.web.exception.BusinessException;
import com.web.security.AuthTokenService;
import com.web.service.AiCustomerServiceClient;
import com.web.service.CustomerServiceService;
import com.web.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.io.IOException;
import java.io.OutputStream;

@Service
public class CustomerServiceServiceImpl implements CustomerServiceService {

    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("0.60");

    private final AiCustomerServiceClient aiCustomerServiceClient;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AuthTokenService authTokenService;

    public CustomerServiceServiceImpl(
            AiCustomerServiceClient aiCustomerServiceClient,
            KnowledgeBaseService knowledgeBaseService,
            AuthTokenService authTokenService) {
        this.aiCustomerServiceClient = aiCustomerServiceClient;
        this.knowledgeBaseService = knowledgeBaseService;
        this.authTokenService = authTokenService;
    }

    @Override
    public CustomerServiceChatResponse chat(String message, String conversationId) {
        return chat(message, conversationId, null);
    }

    @Override
    public CustomerServiceChatResponse chat(String message, String conversationId, String authToken) {
        String normalizedMessage = normalizeMessage(message);
        CustomerServiceChatRequest request = new CustomerServiceChatRequest();
        request.setMessage(normalizedMessage);
        request.setConversationId(conversationId);
        request.setAuthToken(authTokenService.normalizeVerifiedBearerTokenOrNull(authToken));
        CustomerServiceChatResponse response = aiCustomerServiceClient.chat(request);
        recordCustomerServiceLog(normalizedMessage, conversationId, response);
        if (response != null && response.getHitLogs() != null && !response.getHitLogs().isEmpty()) {
            knowledgeBaseService.recordHitLogs(normalizedMessage, conversationId, response.getHitLogs());
        } else if (shouldRecordMiss(response)) {
            try {
                knowledgeBaseService.recordMissedQuestion(
                        normalizedMessage,
                        conversationId,
                        response.getConfidence(),
                        response.getFallbackReason());
            } catch (RuntimeException ignored) {
                // Miss logging is observability only and must not break the customer service reply.
            }
        }
        return response;
    }

    private void recordCustomerServiceLog(String message, String conversationId, CustomerServiceChatResponse response) {
        if (response == null || normalizeAuthToken(response.getRoute()) == null || normalizeAuthToken(response.getSourceType()) == null) {
            return;
        }
        String sourceId = null;
        if (response.getCitations() != null && !response.getCitations().isEmpty()) {
            CustomerServiceCitation citation = response.getCitations().get(0);
            if (citation != null) {
                sourceId = citation.getSourceId();
            }
        }
        try {
            knowledgeBaseService.recordCustomerServiceLog(
                    message,
                    conversationId,
                    response.getRoute(),
                    response.getSourceType(),
                    sourceId,
                    response.getConfidence(),
                    response.getFallbackReason());
        } catch (RuntimeException ignored) {
            // Query logging must not break customer service replies when an older database schema is still running.
        }
    }

    @Override
    public void streamChat(String message, String conversationId, OutputStream outputStream) throws IOException {
        streamChat(message, conversationId, null, outputStream);
    }

    @Override
    public void streamChat(String message, String conversationId, String authToken, OutputStream outputStream) throws IOException {
        String normalizedMessage = normalizeMessage(message);
        CustomerServiceChatRequest request = new CustomerServiceChatRequest();
        request.setMessage(normalizedMessage);
        request.setConversationId(conversationId);
        request.setAuthToken(authTokenService.normalizeVerifiedBearerTokenOrNull(authToken));
        aiCustomerServiceClient.streamChat(request, outputStream);
    }

    private String normalizeMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_FAILED", "Message is required");
        }
        String normalized = message.trim();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("VALIDATION_FAILED", "Message length must be 500 characters or fewer");
        }
        return normalized;
    }

    private String normalizeAuthToken(String authToken) {
        if (authToken == null || authToken.trim().isEmpty()) {
            return null;
        }
        return authToken.trim();
    }

    private boolean shouldRecordMiss(CustomerServiceChatResponse response) {
        if (response == null) {
            return false;
        }
        if (response.getFallbackReason() != null && !response.getFallbackReason().trim().isEmpty()) {
            return true;
        }
        return response.getConfidence() != null
                && response.getConfidence().compareTo(LOW_CONFIDENCE_THRESHOLD) < 0;
    }
}
