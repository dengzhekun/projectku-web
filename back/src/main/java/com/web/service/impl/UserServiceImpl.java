package com.web.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.web.exception.BusinessException;
import com.web.mapper.UserMapper;
import com.web.pojo.User;
import com.web.service.UserService;
import com.web.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WalletService walletService;

    @Override
    public User login(String account, String password) {
        User user = userMapper.getByAccount(account);
        if (user == null) {
            throw new BusinessException("UNAUTHORIZED", "用户不存在或密码错误");
        }
        
        // 使用 Hutool 简单 MD5 校验
        String encryptedPassword = DigestUtil.md5Hex(password);
        if (!user.getPassword().equals(encryptedPassword)) {
            throw new BusinessException("UNAUTHORIZED", "用户不存在或密码错误");
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(String account, String password, String nickname) {
        User existing = userMapper.getByAccount(account);
        if (existing != null) {
            throw new BusinessException("VALIDATION_FAILED", "账号已存在");
        }
        
        User user = new User();
        user.setAccount(account);
        user.setPassword(DigestUtil.md5Hex(password));
        user.setNickname(nickname);
        
        userMapper.insert(user);
        walletService.getOrCreateWallet(user.getId());
        return user;
    }

    @Override
    public User getUserById(Long id) {
        return userMapper.getById(id);
    }

    @Override
    public User getUserByAccount(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        return userMapper.getByAccount(account.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String account, String newPassword) {
        if (account == null || account.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "账号不能为空");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("VALIDATION_FAILED", "密码长度至少 6 位");
        }

        User user = userMapper.getByAccount(account.trim());
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "账号不存在");
        }
        user.setPassword(DigestUtil.md5Hex(newPassword));
        int updated = userMapper.updatePassword(user);
        if (updated != 1) {
            throw new BusinessException("USER_NOT_FOUND", "账号不存在");
        }
    }
}
