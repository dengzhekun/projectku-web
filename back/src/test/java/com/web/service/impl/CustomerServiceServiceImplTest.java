package com.web.service.impl;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.web.dto.CustomerServiceChatRequest;
import com.web.dto.CustomerServiceChatResponse;
import com.web.dto.CustomerServiceHitLog;
import com.web.exception.BusinessException;
import com.web.security.AuthTokenService;
import com.web.service.AiCustomerServiceClient;
import com.web.service.KnowledgeBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceServiceImplTest {

    private static final byte[] JWT_KEY = "projectku_secret_key".getBytes();

    @Mock
    private AiCustomerServiceClient aiCustomerServiceClient;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    private CustomerServiceServiceImpl customerServiceService;

    @BeforeEach
    void setUp() {
        customerServiceService = new CustomerServiceServiceImpl(
                aiCustomerServiceClient,
                knowledgeBaseService,
                new AuthTokenService());
    }

    @Test
    void chatRejectsBlankMessage() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> customerServiceService.chat("   ", null));

        assertEquals("VALIDATION_FAILED", exception.getCode());
        verifyNoInteractions(aiCustomerServiceClient);
        verifyNoInteractions(knowledgeBaseService);
    }

    @Test
    void chatReturnsStructuredReplyFromAiGateway() {
        CustomerServiceChatResponse gatewayReply = new CustomerServiceChatResponse();
        gatewayReply.setAnswer("Please check the order detail page first.");
        gatewayReply.setConfidence(new BigDecimal("0.92"));
        gatewayReply.setRoute("order");
        gatewayReply.setSourceType("business");
        gatewayReply.setRetrievalTrace(Map.of(
                "attributionStatus", "chunk_level",
                "selectedSourceIds", List.of("kb:5:3:1")));

        when(aiCustomerServiceClient.chat(any(CustomerServiceChatRequest.class))).thenReturn(gatewayReply);

        String token = "Bearer " + tokenFor(12L, "user@example.com");

        CustomerServiceChatResponse response = customerServiceService.chat("Request refund", "conversation-1", token);

        assertEquals("Please check the order detail page first.", response.getAnswer());
        assertEquals("chunk_level", response.getRetrievalTrace().get("attributionStatus"));
        ArgumentCaptor<CustomerServiceChatRequest> requestCaptor =
                ArgumentCaptor.forClass(CustomerServiceChatRequest.class);
        verify(aiCustomerServiceClient).chat(requestCaptor.capture());
        assertEquals("Request refund", requestCaptor.getValue().getMessage());
        assertEquals("conversation-1", requestCaptor.getValue().getConversationId());
        assertEquals(token, requestCaptor.getValue().getAuthToken());
        verify(knowledgeBaseService).recordCustomerServiceLog(
                "Request refund",
                "conversation-1",
                "order",
                "business",
                null,
                new BigDecimal("0.92"),
                null);
    }

    @Test
    void chatRejectsInvalidAuthTokenBeforeCallingAiGateway() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> customerServiceService.chat("我的订单到哪了", "conversation-auth", "Bearer fake-token"));

        assertEquals("UNAUTHORIZED", exception.getCode());
        verifyNoInteractions(aiCustomerServiceClient);
    }

    @Test
    void chatPersistsHitLogsReturnedByAiGateway() {
        CustomerServiceChatResponse gatewayReply = new CustomerServiceChatResponse();
        gatewayReply.setAnswer("Seven-day return is supported.");
        gatewayReply.setConfidence(new BigDecimal("0.95"));

        CustomerServiceHitLog hitLog = new CustomerServiceHitLog();
        hitLog.setDocumentId(8L);
        hitLog.setChunkId(81L);
        gatewayReply.setHitLogs(List.of(hitLog));

        when(aiCustomerServiceClient.chat(any(CustomerServiceChatRequest.class))).thenReturn(gatewayReply);

        customerServiceService.chat("Seven-day return rule", "conversation-2");

        verify(knowledgeBaseService).recordHitLogs(
                "Seven-day return rule",
                "conversation-2",
                gatewayReply.getHitLogs());
    }

    @Test
    void chatRecordsMissedQuestionWhenAiGatewayFallsBackWithoutHitLogs() {
        CustomerServiceChatResponse gatewayReply = new CustomerServiceChatResponse();
        gatewayReply.setAnswer("I cannot confirm this from the current knowledge base.");
        gatewayReply.setConfidence(new BigDecimal("0.56"));
        gatewayReply.setFallbackReason("No matching knowledge was found. The answer uses generic rules only.");

        when(aiCustomerServiceClient.chat(any(CustomerServiceChatRequest.class))).thenReturn(gatewayReply);

        customerServiceService.chat("Can coupons be stacked with balance payment?", "conversation-miss");

        verify(knowledgeBaseService).recordMissedQuestion(
                "Can coupons be stacked with balance payment?",
                "conversation-miss",
                new BigDecimal("0.56"),
                "No matching knowledge was found. The answer uses generic rules only.");
    }

    @Test
    void chatStillReturnsReplyWhenMissLogPersistenceFails() {
        CustomerServiceChatResponse gatewayReply = new CustomerServiceChatResponse();
        gatewayReply.setAnswer("Please provide a more specific product name.");
        gatewayReply.setConfidence(new BigDecimal("0.62"));
        gatewayReply.setRoute("product");
        gatewayReply.setSourceType("product");
        gatewayReply.setFallbackReason("No realtime product match for plain apple price query.");

        when(aiCustomerServiceClient.chat(any(CustomerServiceChatRequest.class))).thenReturn(gatewayReply);
        doThrow(new RuntimeException("kb_miss_log missing"))
                .when(knowledgeBaseService)
                .recordMissedQuestion(
                        "Apple price?",
                        "conversation-miss-failed",
                        new BigDecimal("0.62"),
                        "No realtime product match for plain apple price query.");

        CustomerServiceChatResponse response =
                customerServiceService.chat("Apple price?", "conversation-miss-failed");

        assertEquals("Please provide a more specific product name.", response.getAnswer());
    }

    @Test
    void streamChatNormalizesMessageAndDelegatesToAiGateway() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CustomerServiceChatResponse gatewayReply = new CustomerServiceChatResponse();
        gatewayReply.setAnswer("Please check the order detail page first.");
        gatewayReply.setConfidence(new BigDecimal("0.92"));
        gatewayReply.setRoute("order");
        gatewayReply.setSourceType("business");

        when(aiCustomerServiceClient.streamChat(any(CustomerServiceChatRequest.class), any()))
                .thenReturn(gatewayReply);

        String token = "Bearer " + tokenFor(12L, "user@example.com");

        customerServiceService.streamChat("  Request refund  ", "conversation-3", token, outputStream);

        ArgumentCaptor<CustomerServiceChatRequest> requestCaptor =
                ArgumentCaptor.forClass(CustomerServiceChatRequest.class);
        verify(aiCustomerServiceClient).streamChat(requestCaptor.capture(), any());
        assertEquals("Request refund", requestCaptor.getValue().getMessage());
        assertEquals("conversation-3", requestCaptor.getValue().getConversationId());
        assertEquals(token, requestCaptor.getValue().getAuthToken());
        verify(knowledgeBaseService).recordCustomerServiceLog(
                "Request refund",
                "conversation-3",
                "order",
                "business",
                null,
                new BigDecimal("0.92"),
                null);
    }

    @Test
    void streamChatPersistsHitLogsFromFinalReply() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CustomerServiceChatResponse gatewayReply = new CustomerServiceChatResponse();
        gatewayReply.setAnswer("Quality issue return shipping is covered by the merchant.");
        gatewayReply.setConfidence(new BigDecimal("0.94"));
        gatewayReply.setRoute("after_sales");
        gatewayReply.setSourceType("knowledge");

        CustomerServiceHitLog hitLog = new CustomerServiceHitLog();
        hitLog.setDocumentId(5L);
        hitLog.setChunkId(855L);
        gatewayReply.setHitLogs(List.of(hitLog));

        when(aiCustomerServiceClient.streamChat(any(CustomerServiceChatRequest.class), any()))
                .thenReturn(gatewayReply);

        customerServiceService.streamChat("售后质量问题退回运费谁承担？", "conversation-stream-hit", outputStream);

        verify(knowledgeBaseService).recordCustomerServiceLog(
                "售后质量问题退回运费谁承担？",
                "conversation-stream-hit",
                "after_sales",
                "knowledge",
                null,
                new BigDecimal("0.94"),
                null);
        verify(knowledgeBaseService).recordHitLogs(
                "售后质量问题退回运费谁承担？",
                "conversation-stream-hit",
                gatewayReply.getHitLogs());
    }

    @Test
    void streamChatRecordsMissedQuestionFromFinalReplyFallback() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CustomerServiceChatResponse gatewayReply = new CustomerServiceChatResponse();
        gatewayReply.setAnswer("I cannot confirm this from the current knowledge base.");
        gatewayReply.setConfidence(new BigDecimal("0.50"));
        gatewayReply.setRoute("knowledge");
        gatewayReply.setSourceType("knowledge");
        gatewayReply.setFallbackReason("No matching knowledge was found. The answer uses generic rules only.");

        when(aiCustomerServiceClient.streamChat(any(CustomerServiceChatRequest.class), any()))
                .thenReturn(gatewayReply);

        customerServiceService.streamChat("余额和优惠券能不能叠加？", "conversation-stream-miss", outputStream);

        verify(knowledgeBaseService).recordMissedQuestion(
                "余额和优惠券能不能叠加？",
                "conversation-stream-miss",
                new BigDecimal("0.50"),
                "No matching knowledge was found. The answer uses generic rules only.");
    }

    private String tokenFor(Long userId, String account) {
        return JWT.create()
                .setPayload("id", userId)
                .setPayload("account", account)
                .setPayload("exp", System.currentTimeMillis() + 7200 * 1000)
                .setSigner(JWTSignerUtil.hs256(JWT_KEY))
                .sign();
    }
}
