package com.web.controller;

import com.web.exception.BusinessException;
import com.web.pojo.User;
import com.web.service.EmailVerificationService;
import com.web.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;

class AuthControllerTest {

    private UserService userService;
    private EmailVerificationService emailVerificationService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        emailVerificationService = mock(EmailVerificationService.class);
        controller = new AuthController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "emailVerificationService", emailVerificationService);
    }

    @Test
    void sendEmailCodeUsesRegisterPurpose() {
        controller.sendEmailCode(Map.of("email", " User@Example.com "));

        verify(emailVerificationService).requestCode("User@Example.com", "REGISTER", null);
    }

    @Test
    void registerWithEmailAccountRequiresEmailCode() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.register(Map.of(
                        "account", "user@example.com",
                        "password", "123456",
                        "nickname", "用户")));

        assertEquals("VALIDATION_FAILED", exception.getCode());
    }

    @Test
    void registerWithEmailAccountVerifiesCodeBeforeCreatingUser() {
        User created = new User();
        created.setId(9L);
        created.setAccount("user@example.com");
        created.setPassword("secret");
        created.setNickname("用户");
        when(userService.register("user@example.com", "123456", "用户")).thenReturn(created);

        controller.register(Map.of(
                "account", "user@example.com",
                "password", "123456",
                "nickname", "用户",
                "emailCode", "123456"));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailVerificationService).verifyCode(eq("user@example.com"), eq("REGISTER"), codeCaptor.capture());
        assertEquals("123456", codeCaptor.getValue());
        verify(userService).register("user@example.com", "123456", "用户");
    }

    @Test
    void passwordResetCodeUsesResetPurposeWhenAccountExists() {
        User user = new User();
        user.setAccount("user@example.com");
        when(userService.getUserByAccount("user@example.com")).thenReturn(user);

        controller.sendPasswordResetCode(Map.of("email", " user@example.com "));

        verify(emailVerificationService).requestCode("user@example.com", "RESET_PASSWORD", null);
    }

    @Test
    void passwordResetCodeDoesNotLeakMissingAccount() {
        when(userService.getUserByAccount("missing@example.com")).thenReturn(null);

        controller.sendPasswordResetCode(Map.of("email", "missing@example.com"));

        verify(emailVerificationService, never()).requestCode(anyString(), anyString(), anyString());
    }

    @Test
    void resetPasswordVerifiesCodeBeforeChangingPassword() {
        controller.resetPassword(Map.of(
                "account", "user@example.com",
                "password", "newpass123",
                "emailCode", "654321"));

        verify(emailVerificationService).verifyCode("user@example.com", "RESET_PASSWORD", "654321");
        verify(userService).resetPassword("user@example.com", "newpass123");
    }

    @Test
    void loginCodeUsesLoginPurposeWhenAccountExists() {
        User user = new User();
        user.setAccount("user@example.com");
        when(userService.getUserByAccount("user@example.com")).thenReturn(user);

        controller.sendLoginCode(Map.of("email", "user@example.com"));

        verify(emailVerificationService).requestCode("user@example.com", "LOGIN", null);
    }

    @Test
    void loginWithCodeVerifiesCodeAndReturnsToken() {
        User user = new User();
        user.setId(10L);
        user.setAccount("user@example.com");
        user.setPassword("secret");
        user.setNickname("用户");
        when(userService.getUserByAccount("user@example.com")).thenReturn(user);

        Map<String, Object> body = controller.loginWithCode(Map.of(
                "account", "user@example.com",
                "emailCode", "112233")).getBody();

        verify(emailVerificationService).verifyCode("user@example.com", "LOGIN", "112233");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertEquals(7200, data.get("expiresIn"));
    }
}
