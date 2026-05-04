package com.web.controller;

import cn.hutool.core.map.MapUtil;
import com.web.service.AdminPaymentOverviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/admin/payments")
public class AdminPaymentController {

    @Autowired
    private AdminPaymentOverviewService overviewService;

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", 200)
                .put("message", "success")
                .put("data", overviewService.getOverview(limit))
                .build());
    }
}
