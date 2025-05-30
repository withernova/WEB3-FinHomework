package com.maka.controller;

import com.maka.service.TaskPosterService;
import com.maka.service.UserService;
import com.maka.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/task")
public class TaskPosterController {

    @Autowired
    private TaskPosterService posterService;

    @Autowired
    private UserService userService;
    
    /**
     * 生成寻人海报
     */
    @PostMapping("/generate-poster")
    @ResponseBody
    public Map<String, Object> generatePoster(@RequestBody Map<String, Object> requestData, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        
        // 检查用户是否已登录
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            response.put("code", 401);
            response.put("msg", "用户未登录");
            return response;
        }
        
        // 基本验证
        String elderName = (String) requestData.get("elderName");
        String lostTime = (String) requestData.get("lostTime");
        String location = (String) requestData.get("location");
        String photoUrl = (String) requestData.get("photoUrl");
        
        if (elderName == null || elderName.trim().isEmpty()) {
            response.put("code", 400);
            response.put("msg", "老人姓名不能为空");
            return response;
        }
        
        if (lostTime == null || lostTime.trim().isEmpty()) {
            response.put("code", 400);
            response.put("msg", "走失时间不能为空");
            return response;
        }
        
        if (location == null || location.trim().isEmpty()) {
            response.put("code", 400);
            response.put("msg", "走失地点不能为空");
            return response;
        }
        
        if (photoUrl == null || photoUrl.trim().isEmpty()) {
            response.put("code", 400);
            response.put("msg", "老人照片不能为空");
            return response;
        }
        
        try {
            // 获取额外信息和模板数据
            String extraInfo = (String) requestData.get("extraInfo");
            @SuppressWarnings("unchecked")
            Map<String, Object> templateData = (Map<String, Object>) requestData.get("templateData");
            
            // 获取当前用户信息以获取联系电话
            User user = userService.getUserByUUID(userId);
            String contactPhone = "110"; // 默认值
            
            if (user != null && user.getPhone() != null && !user.getPhone().trim().isEmpty()) {
                contactPhone = user.getPhone();
            } else {
                // 如果用户没有电话，使用请求中提供的电话（如果有）
                String requestPhone = (String) requestData.get("contactPhone");
                if (requestPhone != null && !requestPhone.trim().isEmpty()) {
                    contactPhone = requestPhone;
                }
            }
            
            // 当前时间
            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            
            // 调用服务生成HTML海报
            Map<String, Object> result = posterService.generateHtmlPoster(
                elderName, lostTime, location, photoUrl, extraInfo, 
                templateData, contactPhone, currentTime, userId);
            
            if ((Boolean) result.get("success")) {
                response.put("code", 0);
                response.put("msg", "海报生成成功");
                response.put("data", result);
            } else {
                response.put("code", 500);
                response.put("msg", result.get("message"));
            }
            
            return response;
        } catch (Exception e) {
            response.put("code", 500);
            response.put("msg", "服务器错误: " + e.getMessage());
            return response;
        }
    }
    
    
}