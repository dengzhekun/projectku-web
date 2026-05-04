package com.web.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.web.exception.BusinessException;
import com.web.pojo.User;
import com.web.service.EmailVerificationService;
import com.web.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    // JWT 签名密钥
    private static final byte[] JWT_KEY = "projectku_secret_key".getBytes();

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> params) {
        String account = (String) params.get("account");
        String password = (String) params.get("password");

        if (account == null || account.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "账号或密码不能为空");
        }
        
        User user = userService.login(account, password);
        return loginResponse(user);
    }

    @PostMapping("/email-code")
    public ResponseEntity<Map<String, Object>> sendEmailCode(@RequestBody Map<String, Object> params) {
        String email = (String) params.get("email");
        if (email == null || email.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "邮箱不能为空");
        }

        emailVerificationService.requestCode(email.trim(), "REGISTER", null);

        Map<String, Object> meta = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("requestId", UUID.randomUUID().toString())
                .build();

        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("data", Map.of("sent", true, "cooldownSeconds", 60))
                .put("meta", meta)
                .build());
    }

    @PostMapping("/password-reset-code")
    public ResponseEntity<Map<String, Object>> sendPasswordResetCode(@RequestBody Map<String, Object> params) {
        String email = (String) params.get("email");
        if (!isValidEmailFormat(email)) {
            throw new BusinessException("VALIDATION_FAILED", "邮箱格式不正确");
        }

        String normalizedEmail = email.trim();
        if (userService.getUserByAccount(normalizedEmail) != null) {
            emailVerificationService.requestCode(normalizedEmail, "RESET_PASSWORD", null);
        }

        Map<String, Object> meta = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("requestId", UUID.randomUUID().toString())
                .build();

        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("data", Map.of("sent", true, "cooldownSeconds", 60))
                .put("meta", meta)
                .build());
    }

    @PostMapping("/login-code")
    public ResponseEntity<Map<String, Object>> sendLoginCode(@RequestBody Map<String, Object> params) {
        String email = (String) params.get("email");
        if (!isValidEmailFormat(email)) {
            throw new BusinessException("VALIDATION_FAILED", "邮箱格式不正确");
        }

        String normalizedEmail = email.trim();
        if (userService.getUserByAccount(normalizedEmail) != null) {
            emailVerificationService.requestCode(normalizedEmail, "LOGIN", null);
        }

        Map<String, Object> meta = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("requestId", UUID.randomUUID().toString())
                .build();

        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("data", Map.of("sent", true, "cooldownSeconds", 60))
                .put("meta", meta)
                .build());
    }

    @PostMapping("/login-with-code")
    public ResponseEntity<Map<String, Object>> loginWithCode(@RequestBody Map<String, Object> params) {
        String account = (String) params.get("account");
        String emailCode = (String) params.get("emailCode");

        if (!isValidEmailFormat(account)) {
            throw new BusinessException("VALIDATION_FAILED", "请填写正确的邮箱账号");
        }
        if (emailCode == null || emailCode.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "请输入邮箱验证码");
        }

        String normalizedAccount = account.trim();
        emailVerificationService.verifyCode(normalizedAccount, "LOGIN", emailCode.trim());
        User user = userService.getUserByAccount(normalizedAccount);
        if (user == null) {
            throw new BusinessException("UNAUTHORIZED", "用户不存在或验证码错误");
        }
        return loginResponse(user);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, Object> params) {
        String account = (String) params.get("account");
        String password = (String) params.get("password");
        String emailCode = (String) params.get("emailCode");

        if (!isValidEmailFormat(account)) {
            throw new BusinessException("VALIDATION_FAILED", "请填写正确的邮箱账号");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException("VALIDATION_FAILED", "密码长度至少 6 位");
        }
        if (emailCode == null || emailCode.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "请输入邮箱验证码");
        }

        String normalizedAccount = account.trim();
        emailVerificationService.verifyCode(normalizedAccount, "RESET_PASSWORD", emailCode.trim());
        userService.resetPassword(normalizedAccount, password);

        Map<String, Object> meta = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("requestId", UUID.randomUUID().toString())
                .build();

        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("data", Map.of("reset", true))
                .put("meta", meta)
                .build());
    }
    
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> params) {
        String account = (String) params.get("account");
        String password = (String) params.get("password");
        String nickname = (String) params.getOrDefault("nickname", "User_" + System.currentTimeMillis());
        String emailCode = (String) params.get("emailCode");

        if (account == null || account.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "账号或密码不能为空");
        }
        if (password.length() < 6) {
            throw new BusinessException("VALIDATION_FAILED", "密码长度至少 6 位");
        }
        if (isEmailAccount(account)) {
            if (emailCode == null || emailCode.isBlank()) {
                throw new BusinessException("VALIDATION_FAILED", "请输入邮箱验证码");
            }
            emailVerificationService.verifyCode(account.trim(), "REGISTER", emailCode.trim());
        }
        
        User user = userService.register(account, password, nickname);
        
        Map<String, Object> userMap = BeanUtil.beanToMap(user, false, true);
        userMap.remove("password");
        
        Map<String, Object> meta = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("requestId", UUID.randomUUID().toString())
                .build();
                
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("data", userMap)
                .put("meta", meta)
                .build());
    }

    private boolean isEmailAccount(String account) {
        return account != null && account.trim().contains("@");
    }

    private boolean isValidEmailFormat(String account) {
        return account != null && account.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private ResponseEntity<Map<String, Object>> loginResponse(User user) {
        // 使用 Hutool 生成 JWT
        String token = JWT.create()
                .setPayload("id", user.getId())
                .setPayload("account", user.getAccount())
                .setPayload("exp", System.currentTimeMillis() + 7200 * 1000) // 2小时过期
                .setSigner(JWTSignerUtil.hs256(JWT_KEY))
                .sign();

        Map<String, Object> userMap = BeanUtil.beanToMap(user, false, true);
        userMap.remove("password"); // 移除敏感信息

        Map<String, Object> data = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("token", token)
                .put("expiresIn", 7200)
                .put("user", userMap)
                .build();

        Map<String, Object> meta = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("requestId", UUID.randomUUID().toString())
                .build();

        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("data", data)
                .put("meta", meta)
                .build());
    }
}
