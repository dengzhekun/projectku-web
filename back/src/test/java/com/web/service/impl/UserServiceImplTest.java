package com.web.service.impl;

import com.web.exception.BusinessException;
import com.web.mapper.UserMapper;
import com.web.pojo.User;
import com.web.pojo.UserWallet;
import com.web.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void registerCreatesWalletWithRegistrationBonus() {
        when(userMapper.getByAccount("new_user")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(88L);
            return 1;
        });
        when(walletService.getOrCreateWallet(88L)).thenReturn(
                new UserWallet(1L, 88L, new BigDecimal("20000.00"), null, null));

        User created = userService.register("new_user", "123456", "新用户");

        assertEquals(88L, created.getId());
        verify(walletService).getOrCreateWallet(88L);
    }

    @Test
    void registerRejectsDuplicateAccountWithoutCreatingWallet() {
        when(userMapper.getByAccount("existing")).thenReturn(new User());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.register("existing", "123456", "重复用户"));

        assertEquals("VALIDATION_FAILED", exception.getCode());
    }

    @Test
    void resetPasswordUpdatesStoredPasswordHash() {
        User user = new User();
        user.setId(7L);
        user.setAccount("user@example.com");
        when(userMapper.getByAccount("user@example.com")).thenReturn(user);
        when(userMapper.updatePassword(any(User.class))).thenReturn(1);

        userService.resetPassword(" user@example.com ", "newpass123");

        verify(userMapper).updatePassword(user);
        assertEquals(cn.hutool.crypto.digest.DigestUtil.md5Hex("newpass123"), user.getPassword());
    }

    @Test
    void resetPasswordRejectsMissingAccount() {
        when(userMapper.getByAccount("missing@example.com")).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.resetPassword("missing@example.com", "newpass123"));

        assertEquals("USER_NOT_FOUND", exception.getCode());
    }
}
