package com.web.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.map.MapUtil;
import com.web.exception.BusinessException;
import com.web.interceptor.AuthInterceptor;
import com.web.dto.UserAddressRequests;
import com.web.pojo.User;
import com.web.pojo.UserAddress;
import com.web.service.WalletService;
import com.web.service.UserAddressService;
import com.web.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户中心与地址接口
 */
@RestController
@RequestMapping("/v1/me")
public class UserController {

    @Autowired
    private UserService userService;
    
    @Autowired
    private UserAddressService userAddressService;

    @Autowired
    private WalletService walletService;

    /**
     * 获取当前登录用户信息
     * GET /v1/me
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        Long userId = AuthInterceptor.getCurrentUserId();
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        
        Map<String, Object> userMap = BeanUtil.beanToMap(user, false, true);
        userMap.remove("password");
        
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", 200)
                .put("message", "success")
                .put("data", userMap)
                .build());
    }

    @GetMapping("/wallet")
    public ResponseEntity<Map<String, Object>> getWallet() {
        Long userId = AuthInterceptor.getCurrentUserId();
        var wallet = walletService.getOrCreateWallet(userId);
        var transactions = walletService.listTransactions(userId);
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", 200)
                .put("message", "success")
                .put("data", MapUtil.builder(new java.util.HashMap<String, Object>())
                        .put("wallet", BeanUtil.beanToMap(wallet, false, true))
                        .put("transactions", transactions.stream().map(item -> BeanUtil.beanToMap(item, false, true)).collect(Collectors.toList()))
                        .build())
                .build());
    }

    /**
     * 获取当前用户的地址列表
     * GET /v1/me/addresses
     */
    @GetMapping("/addresses")
    public ResponseEntity<Map<String, Object>> getAddresses() {
        Long userId = AuthInterceptor.getCurrentUserId();
        List<UserAddress> list = userAddressService.getListByUserId(userId);
        
        List<Map<String, Object>> mapList = list.stream()
                .map(addr -> BeanUtil.beanToMap(addr, false, true))
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", 200)
                .put("message", "success")
                .put("data", mapList)
                .build());
    }

    /**
     * 新增地址
     * POST /v1/me/addresses
     */
    @PostMapping("/addresses")
    public ResponseEntity<Map<String, Object>> addAddress(@RequestBody UserAddressRequests.CreateRequest request) {
        Long userId = AuthInterceptor.getCurrentUserId();
        UserAddress address = toUserAddress(request);
        address.setUserId(userId);
        
        UserAddress saved = userAddressService.addAddress(address);
        
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", 200)
                .put("message", "success")
                .put("data", BeanUtil.beanToMap(saved, false, true))
                .build());
    }

    /**
     * 修改地址
     * PUT /v1/me/addresses/{id}
     */
    @PutMapping("/addresses/{id}")
    public ResponseEntity<Map<String, Object>> updateAddress(@PathVariable Long id, @RequestBody UserAddressRequests.UpdateRequest request) {
        Long userId = AuthInterceptor.getCurrentUserId();
        UserAddress address = toUserAddress(request);
        address.setId(id);
        address.setUserId(userId);
        
        boolean success = userAddressService.updateAddress(address);
        
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", success ? 200 : 500)
                .put("message", success ? "success" : "failed")
                .build());
    }

    /**
     * 删除地址
     * DELETE /v1/me/addresses/{id}
     */
    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Map<String, Object>> deleteAddress(@PathVariable Long id) {
        Long userId = AuthInterceptor.getCurrentUserId();
        boolean success = userAddressService.deleteAddress(id, userId);
        
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", success ? 200 : 500)
                .put("message", success ? "success" : "failed")
                .build());
    }

    private UserAddress toUserAddress(UserAddressRequests.CreateRequest request) {
        UserAddress address = new UserAddress();
        address.setReceiver(trimToNull(request.getReceiver()));
        address.setPhone(trimToNull(request.getPhone()));
        address.setRegion(normalizeRegion(request.getRegion(), request.getProvince(), request.getCity(), request.getDistrict()));
        address.setDetail(trimToNull(request.getDetail()));
        address.setIsDefault(normalizeDefaultFlag(request.getIsDefault()));
        validateAddress(address, true);
        return address;
    }

    private UserAddress toUserAddress(UserAddressRequests.UpdateRequest request) {
        UserAddress address = new UserAddress();
        address.setReceiver(trimToNull(request.getReceiver()));
        address.setPhone(trimToNull(request.getPhone()));
        address.setRegion(normalizeRegion(request.getRegion(), request.getProvince(), request.getCity(), request.getDistrict()));
        address.setDetail(trimToNull(request.getDetail()));
        address.setIsDefault(request.getIsDefault() == null ? null : normalizeDefaultFlag(request.getIsDefault()));
        validateAddress(address, false);
        return address;
    }

    private void validateAddress(UserAddress address, boolean requireAllFields) {
        if (requireAllFields) {
            if (address.getReceiver() == null) {
                throw new BusinessException("VALIDATION_FAILED", "收货人不能为空");
            }
            if (address.getPhone() == null) {
                throw new BusinessException("VALIDATION_FAILED", "手机号不能为空");
            }
            if (address.getRegion() == null) {
                throw new BusinessException("VALIDATION_FAILED", "所在地区不能为空");
            }
            if (address.getDetail() == null) {
                throw new BusinessException("VALIDATION_FAILED", "详细地址不能为空");
            }
        }
        if (address.getPhone() != null && !address.getPhone().matches("^1\\d{10}$")) {
            throw new BusinessException("VALIDATION_FAILED", "手机号格式不正确");
        }
    }

    private String normalizeRegion(String region, String province, String city, String district) {
        String direct = trimToNull(region);
        if (direct != null) {
            return direct;
        }
        return trimToNull(String.join(" ",
                nullToBlank(province),
                nullToBlank(city),
                nullToBlank(district)).replaceAll("\\s+", " "));
    }

    private Integer normalizeDefaultFlag(Integer isDefault) {
        return isDefault != null && isDefault == 1 ? 1 : 0;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value.trim();
    }
}
