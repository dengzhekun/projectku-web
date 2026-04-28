package com.web.service;

import com.web.dto.CustomerServiceChatRequest;
import com.web.dto.CustomerServiceChatResponse;

import java.io.IOException;
import java.io.OutputStream;

public interface AiCustomerServiceClient {
    CustomerServiceChatResponse chat(CustomerServiceChatRequest request);

    void streamChat(CustomerServiceChatRequest request, OutputStream outputStream) throws IOException;
}
