package com.maka.service;


import com.maka.pojo.User;
import com.maka.dto.UserRegistrationRequest;
import com.maka.dto.UserRegistrationResponse;
import org.springframework.stereotype.Service;


/**
 * @author yang
 */
@Service
public interface UserService {
    /**
     * 发送手机验证码
     * @param phone 手机号
     * @return 是否发送成功
     */
    boolean sendVerificationCode(String phone);
    
    /**
     * 用户注册
     * @param request 注册请求
     * @return 注册响应
     */
    UserRegistrationResponse register(UserRegistrationRequest request);
    
    /**
     * 用户登录
     * @param username 用户名或手机号
     * @param password 密码
     * @return 登录成功的用户，失败返回null
     */
    User login(String username, String password);
}
