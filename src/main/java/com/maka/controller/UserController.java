package com.maka.controller;

import com.maka.component.MessageResponse;
import com.maka.query.PageRequest;
import com.maka.query.PageResponse;
import com.maka.query.SimpleUser;
import com.maka.query.UserInfo;
import com.maka.service.UserService;
import com.maka.service.impl.UserServiceImpl;
import com.maka.dto.UserRegistrationRequest;
import com.maka.dto.UserRegistrationResponse;
import com.maka.pojo.User;

import com.mysql.cj.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
/**
 * @author yang
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @PostMapping("/sendVerificationCode")
    public ResponseEntity<?> sendVerificationCode(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        
        if (phone == null || phone.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "手机号不能为空");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        boolean sent = userService.sendVerificationCode(phone);
        
        Map<String, Object> result = new HashMap<>();
        if (sent) {
            result.put("success", true);
            result.put("message", "验证码已发送");
            return ResponseEntity.ok(result);
        } else {
            result.put("success", false);
            result.put("message", "验证码发送失败");
            return ResponseEntity.unprocessableEntity().body(result);
        }
    }
            
    @PostMapping("/register")
    public ResponseEntity<UserRegistrationResponse> register(@RequestBody UserRegistrationRequest request) {
        UserRegistrationResponse response = userService.register(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request, HttpSession session) {
        String username = request.get("username");
        String password = request.get("password");
        Boolean remember = Boolean.valueOf(request.get("remember"));
        
        User user = userService.login(username, password);
        
        if (user != null) {
            // 登录成功，设置session
            session.setAttribute("userId", user.getUuid());
            session.setAttribute("userType", user.getUserType());
            
            // 如果勾选"记住我"，延长session过期时间
            if (remember != null && remember) {
                session.setMaxInactiveInterval(7 * 24 * 60 * 60); // 设置为7天
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "登录成功");
            response.put("userType", user.getUserType());
            
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "用户名或密码错误");
            return ResponseEntity.badRequest().body(result);
        }
    }

    // 获取当前登录用户
    @GetMapping("/getCurrentUser")
    public User getCurrentUser(HttpSession session) {
        return (User) session.getAttribute("userId");
    }
    
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已退出登录");
        return ResponseEntity.ok(result);
    }
}