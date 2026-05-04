package com.web.service;

import com.web.pojo.User;

public interface UserService {
    User login(String account, String password);
    
    User register(String account, String password, String nickname);
    
    User getUserById(Long id);

    User getUserByAccount(String account);

    void resetPassword(String account, String newPassword);
}
