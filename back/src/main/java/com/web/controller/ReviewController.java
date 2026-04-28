package com.web.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.map.MapUtil;
import com.web.exception.BusinessException;
import com.web.interceptor.AuthInterceptor;
import com.web.pojo.Review;
import com.web.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> params) {
        Long userId = AuthInterceptor.getCurrentUserId();
        if (!params.containsKey("orderId") || params.get("orderId") == null) {
            throw new BusinessException("VALIDATION_FAILED", "orderId 不能为空");
        }
        if (!params.containsKey("productId") || params.get("productId") == null) {
            throw new BusinessException("VALIDATION_FAILED", "productId 不能为空");
        }
        Long orderId;
        Long productId;
        Integer rating;
        try {
            orderId = Long.valueOf(params.get("orderId").toString());
            productId = Long.valueOf(params.get("productId").toString());
            rating = params.containsKey("rating") && params.get("rating") != null ? Integer.valueOf(params.get("rating").toString()) : 5;
        } catch (Exception e) {
            throw new BusinessException("VALIDATION_FAILED", "参数非法");
        }
        String content = params.getOrDefault("content", "").toString();
        String images = params.getOrDefault("images", "[]").toString();
        
        Review r = reviewService.create(userId, orderId, productId, rating, content, images);
        Map<String, Object> meta = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("requestId", UUID.randomUUID().toString())
                .build();
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", 200)
                .put("message", "success")
                .put("data", BeanUtil.beanToMap(r, false, true))
                .put("meta", meta)
                .build());
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(required = false) Long productId,
                                                    @RequestParam(required = false) Long orderId) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException("VALIDATION_FAILED", "page/size 参数非法");
        }
        Long userId = AuthInterceptor.getCurrentUserId();
        List<Review> list;

        // 如果提供了 productId 且不是专门查“我的评价”，则返回该商品的所有评价
        if (productId != null && orderId == null) {
            list = reviewService.listByProduct(page, size, productId);
        } else {
            // 否则返回当前用户的评价（需要登录）
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Unauthorized"));
            }
            list = reviewService.list(userId, page, size, productId, orderId);
        }

        List<Map<String, Object>> mapList = list.stream()
                .map(x -> BeanUtil.beanToMap(x, false, true))
                .collect(Collectors.toList());
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", 200)
                .put("message", "success")
                .put("data", mapList)
                .build());
    }
}
