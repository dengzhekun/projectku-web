package com.web.service.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.web.config.AiServiceProperties;
import com.web.dto.CustomerServiceChatRequest;
import com.web.pojo.KbChunk;
import com.web.pojo.KbDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

        client.indexDocumentChunks(document, List.of(chunk));

        assertTrue(body.get().contains("平台承担退回运费"), body.get());
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
}
