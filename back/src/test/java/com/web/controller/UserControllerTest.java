package com.web.controller;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.web.dto.UserAddressRequests;
import com.web.exception.BusinessException;
import com.web.interceptor.AuthInterceptor;
import com.web.pojo.UserAddress;
import com.web.security.AuthTokenService;
import com.web.service.UserAddressService;
import com.web.service.UserService;
import com.web.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private static final byte[] JWT_KEY = "projectku_secret_key".getBytes();

    private UserAddressService userAddressService;
    private UserController controller;
    private AuthInterceptor authInterceptor;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        userAddressService = mock(UserAddressService.class);
        controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", mock(UserService.class));
        ReflectionTestUtils.setField(controller, "userAddressService", userAddressService);
        ReflectionTestUtils.setField(controller, "walletService", mock(WalletService.class));
        authInterceptor = new AuthInterceptor(new AuthTokenService());
        response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/me/addresses");
        request.addHeader("Authorization", "Bearer " + tokenFor(42L, "selftest"));
        authInterceptor.preHandle(request, response, null);
    }

    @AfterEach
    void cleanUp() throws Exception {
        authInterceptor.afterCompletion(new MockHttpServletRequest(), response, null, null);
    }

    @Test
    void addAddressAcceptsSplitRegionFields() {
        UserAddress saved = new UserAddress();
        saved.setId(18L);
        saved.setReceiver("Tester");
        saved.setPhone("13800000000");
        saved.setRegion("Guangdong Shenzhen Nanshan");
        saved.setDetail("Test Road 1");
        saved.setIsDefault(1);
        when(userAddressService.addAddress(argThat(address ->
                "Tester".equals(address.getReceiver())
                        && "13800000000".equals(address.getPhone())
                        && "Guangdong Shenzhen Nanshan".equals(address.getRegion())
                        && "Test Road 1".equals(address.getDetail())
                        && Integer.valueOf(1).equals(address.getIsDefault())))).thenReturn(saved);

        UserAddressRequests.CreateRequest request = new UserAddressRequests.CreateRequest();
        request.setReceiver("Tester");
        request.setPhone("13800000000");
        request.setProvince("Guangdong");
        request.setCity("Shenzhen");
        request.setDistrict("Nanshan");
        request.setDetail("Test Road 1");
        request.setIsDefault(1);

        Map<String, Object> body = controller.addAddress(request).getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertEquals(18L, data.get("id"));
        assertEquals("Guangdong Shenzhen Nanshan", data.get("region"));
    }

    @Test
    void addAddressRejectsMissingRegionBeforeDatabaseInsert() {
        UserAddressRequests.CreateRequest request = new UserAddressRequests.CreateRequest();
        request.setReceiver("Tester");
        request.setPhone("13800000000");
        request.setDetail("Test Road 1");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                controller.addAddress(request));

        assertEquals("VALIDATION_FAILED", exception.getCode());
    }

    private String tokenFor(Long userId, String account) {
        return JWT.create()
                .setPayload("id", userId)
                .setPayload("account", account)
                .setPayload("exp", System.currentTimeMillis() + 7200 * 1000)
                .setSigner(JWTSignerUtil.hs256(JWT_KEY))
                .sign();
    }
}
