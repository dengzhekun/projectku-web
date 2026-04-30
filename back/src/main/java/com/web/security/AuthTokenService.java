package com.web.security;

import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.web.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class AuthTokenService {

    private static final byte[] JWT_KEY = "projectku_secret_key".getBytes();

    public VerifiedToken verifyRequired(String authorization) {
        if (StrUtil.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException("UNAUTHORIZED", "未登录或令牌失效");
        }

        String token = authorization.substring(7);
        try {
            boolean verify = JWTUtil.verify(token, JWT_KEY);
            if (!verify) {
                throw new BusinessException("UNAUTHORIZED", "令牌签名无效");
            }

            JWT jwt = JWTUtil.parseToken(token);
            Long exp = Long.valueOf(jwt.getPayload("exp").toString());
            if (System.currentTimeMillis() > exp) {
                throw new BusinessException("UNAUTHORIZED", "令牌已过期");
            }

            Long userId = Long.valueOf(jwt.getPayload("id").toString());
            String account = String.valueOf(jwt.getPayload("account"));
            return new VerifiedToken(userId, account, "Bearer " + token);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("UNAUTHORIZED", "解析令牌失败");
        }
    }

    public String normalizeVerifiedBearerTokenOrNull(String authorization) {
        if (StrUtil.isBlank(authorization)) {
            return null;
        }
        return verifyRequired(authorization).bearerToken();
    }

    public record VerifiedToken(Long userId, String account, String bearerToken) {
    }
}
