package com.web.service.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.web.config.AiServiceProperties;
import com.web.dto.CustomerServiceChatRequest;
import com.web.dto.CustomerServiceChatResponse;
import com.web.pojo.KbChunk;
import com.web.pojo.KbDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServiceClientEncodingTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void customerServiceClientSendsUtf8Json() {
        AtomicReference<String> body = new AtomicReference<>("");
        server.createContext("/chat", exchange -> {
            body.set(readUtf8(exchange));
            writeJson(exchange, "{\"answer\":\"ok\",\"confidence\":0.9}");
        });

        AiCustomerServiceClientImpl client = new AiCustomerServiceClientImpl(properties());
        CustomerServiceChatRequest request = new CustomerServiceChatRequest();
        request.setMessage("我收到商品就是坏的，退货运费是谁承担？");

        client.chat(request);

        assertTrue(body.get().contains("退货运费是谁承担"), body.get());
    }

    @Test
    void knowledgeBaseClientSendsUtf8Json() {
        AtomicReference<String> body = new AtomicReference<>("");
        server.createContext("/internal/index", exchange -> {
            body.set(readUtf8(exchange));
            writeJson(exchange, "{\"documentId\":5,\"indexedChunkCount\":1,\"embeddingProvider\":\"remote_http\",\"vectorCollection\":\"ecommerce_kb_v1\"}");
        });

        AiKnowledgeBaseClientImpl client = new AiKnowledgeBaseClientImpl(properties());
        KbDocument document = new KbDocument();
        document.setId(5L);
        document.setTitle("售后政策");
        document.setCategory("after_sales");
        document.setVersion(1);
        KbChunk chunk = new KbChunk(11L, 5L, 0, "质量问题通过审核后，平台承担退回运费。", 21, "active", null);

        client.indexDocumentChunks(document, List.of(chunk), true);

        assertTrue(body.get().contains("平台承担退回运费"), body.get());
        assertTrue(body.get().contains("\"recoverMapping\":true"), body.get());
    }

    @Test
    void customerServiceStreamReturnsFinalReplyAndPreservesSse() throws Exception {
        server.createContext("/chat/stream", exchange -> {
            readUtf8(exchange);
            writeEventStream(exchange,
                    "data: {\"type\":\"delta\",\"text\":\"质量问题\"}\n\n" +
                            "data: {\"type\":\"final\",\"reply\":{\"answer\":\"质量问题通过审核后，退货运费通常由商家承担。\",\"confidence\":0.94,\"route\":\"after_sales\",\"sourceType\":\"knowledge\",\"hitLogs\":[{\"documentId\":5,\"chunkId\":855}]}}\n\n");
        });

        AiCustomerServiceClientImpl client = new AiCustomerServiceClientImpl(properties());
        CustomerServiceChatRequest request = new CustomerServiceChatRequest();
        request.setMessage("售后质量问题退回运费谁承担？");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        CustomerServiceChatResponse response = client.streamChat(request, outputStream);

        String streamed = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(streamed.contains("\"type\":\"delta\""), streamed);
        assertTrue(streamed.contains("\"type\":\"final\""), streamed);
        assertEquals("质量问题通过审核后，退货运费通常由商家承担。", response.getAnswer());
        assertEquals("after_sales", response.getRoute());
        assertEquals("knowledge", response.getSourceType());
        assertEquals(5L, response.getHitLogs().get(0).getDocumentId());
        assertEquals(855L, response.getHitLogs().get(0).getChunkId());
    }

    @Test
    void customerServiceStreamParsesFinalReplyWhenSseDataIsSplitAcrossLines() throws Exception {
        server.createContext("/chat/stream", exchange -> {
            readUtf8(exchange);
            writeEventStream(exchange,
                    "data: {\"type\":\"final\",\n" +
                            "data: \"reply\":{\"answer\":\"ok\",\"confidence\":0.9,\"route\":\"knowledge\",\"sourceType\":\"knowledge\"}}\n\n");
        });

        AiCustomerServiceClientImpl client = new AiCustomerServiceClientImpl(properties());
        CustomerServiceChatRequest request = new CustomerServiceChatRequest();
        request.setMessage("物流规则是什么？");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        CustomerServiceChatResponse response = client.streamChat(request, outputStream);

        assertEquals("ok", response.getAnswer());
        assertEquals("knowledge", response.getRoute());
        assertEquals("knowledge", response.getSourceType());
        assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("data: \"reply\""));
    }

    private AiServiceProperties properties() {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setBaseUrl(baseUrl);
        properties.setTimeoutSeconds(5);
        return properties;
    }

    private String readUtf8(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void writeEventStream(HttpExchange exchange, String events) throws IOException {
        byte[] bytes = events.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
