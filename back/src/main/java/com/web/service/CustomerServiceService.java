package com.web.service;

import com.web.dto.CustomerServiceChatResponse;

import java.io.IOException;
import java.io.OutputStream;

public interface CustomerServiceService {
    default CustomerServiceChatResponse chat(String message, String conversationId) {
        return chat(message, conversationId, null);
    }

    CustomerServiceChatResponse chat(String message, String conversationId, String authToken);

    default void streamChat(String message, String conversationId, OutputStream outputStream) throws IOException {
        streamChat(message, conversationId, null, outputStream);
    }

    void streamChat(String message, String conversationId, String authToken, OutputStream outputStream) throws IOException;
}
