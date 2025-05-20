package com.maka.service.impl;


import com.maka.component.MessageResponse;
import com.maka.mapper.UserMapper;
import com.maka.pojo.User;
import com.maka.query.UserInfo;
import com.maka.service.UserService;
import com.maka.vo.RescuingUser;
import com.maka.pojo.Family;
import com.maka.pojo.Rescuer;
import com.maka.service.SmsService;
import com.maka.dto.UserRegistrationRequest;
import com.maka.dto.UserRegistrationResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * @author admin
 */
@Service
public class UserServiceImpl implements UserService {
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private SmsService smsService;
    
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    @Override
    public boolean sendVerificationCode(String phone) {
        return smsService.sendVerificationCode(phone);
    }
    
    @Override
    @Transactional
    public UserRegistrationResponse register(UserRegistrationRequest request) {
        UserRegistrationResponse response = new UserRegistrationResponse();
        
        // 1. 验证用户名是否已存在
        if (userMapper.checkUsernameExists(request.getUsername()) > 0) {
            response.setSuccess(false);
            response.setMessage("用户名已存在");
            return response;
        }
        
        // 2. 验证手机号是否已存在
        if (userMapper.checkPhoneExists(request.getPhone()) > 0) {
            response.setSuccess(false);
            response.setMessage("手机号已注册");
            return response;
        }
        
        // 3. 验证短信验证码
        // if (!smsService.verifyCode(request.getPhone(), request.getVerificationCode())) {
        //     response.setSuccess(false);
        //     response.setMessage("验证码错误或已过期");
        //     return response;
        // }
        
        // 4. 创建用户
        String userUuid = UUID.randomUUID().toString();
        
        User user = new User();
        user.setUuid(userUuid);
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword())); // 加密密码
        user.setPhone(request.getPhone());
        user.setGender(request.getGender() != null ? request.getGender() : "M"); // 默认男性
        user.setUserType(request.getUserType());
        
        userMapper.insertUser(user);
        
        // 5. 根据角色创建相应记录
        if ("rescuer".equals(request.getUserType())) {
            Rescuer rescuer = new Rescuer();
            rescuer.setUuid(userUuid);
            rescuer.setName(request.getName());
            rescuer.setStatus("visitor");
            rescuer.setLocation(request.getLocation());
            rescuer.setTaskIds(new ArrayList<>());
            
            userMapper.insertRescuer(rescuer);
        } else if ("family".equals(request.getUserType())) {
            Family family = new Family();
            family.setUuid(userUuid);
            family.setName(request.getName());
            family.setTaskIds(new ArrayList<>());
            
            userMapper.insertFamily(family);
        }
        
        // 6. 设置响应
        response.setSuccess(true);
        response.setMessage("注册成功");
        response.setUuid(userUuid);
        
        return response;
    }
    
    @Override
    public User login(String username, String password) {
        User user = null;
        
        // 支持用户名或手机号登录
        if (username.matches("^1\\d{10}$")) {
            // 手机号登录
            user = userMapper.getUserByPhone(username);
        } else {
            // 用户名登录
            user = userMapper.getUserByUsername(username);
        }
        
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        
        return null;
    }
}
