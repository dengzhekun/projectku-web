package com.web.controller;

import cn.hutool.core.map.MapUtil;
import com.web.dto.CustomerServiceChatResponse;
import com.web.dto.CustomerServiceRequests;
import com.web.service.CustomerServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "在线客服", description = "AI 在线客服问答接口")
@RestController
@RequestMapping("/v1/customer-service")
public class CustomerServiceController {

    private final CustomerServiceService customerServiceService;

    public CustomerServiceController(CustomerServiceService customerServiceService) {
        this.customerServiceService = customerServiceService;
    }

    @Operation(summary = "咨询在线客服", description = "通过 AI 服务生成客服回答")
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestBody CustomerServiceRequests.ChatRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        CustomerServiceChatResponse data =
                customerServiceService.chat(request.getMessage(), request.getConversationId(), authorization);
        Map<String, Object> result = MapUtil.builder(new HashMap<String, Object>())
                .put("code", 200)
                .put("message", "success")
                .put("data", data)
                .build();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "流式咨询在线客服", description = "通过 SSE 透传 AI 客服流式回答")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamChat(
            @RequestBody CustomerServiceRequests.ChatRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StreamingResponseBody body = outputStream ->
                customerServiceService.streamChat(request.getMessage(), request.getConversationId(), authorization, outputStream);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }
}
