package com.maka.controller;

import com.maka.pojo.Task;
import com.maka.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest; // 别忘了导包

import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * 走失老人任务控制器
 */
@Controller
@RequestMapping("/task")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @GetMapping("/task-publish")
    public String showTaskPublishPage(HttpSession session) {
        // 检查用户是否已登录
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            // 如果未登录，重定向到登录页面
            return "redirect:/login";
        }
        
        // 返回任务发布页面
        return "task/task-publish";
    }
    
    /**
     * 任务管理页面
     */
    @GetMapping("/task-manage")
    public String showTaskManagePage(HttpSession session) {
        // 检查用户是否已登录
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            // 如果未登录，重定向到登录页面
            return "redirect:/login";
        }
        
        // 返回任务管理页面
        return "task/task-manage";
    }
    
    /**
     * 获取当前用户的任务列表
     */
    @GetMapping("/list")
    @ResponseBody
    public Map<String, Object> getTaskList(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "elderName", required = false) String elderName,
            @RequestParam(value = "status", required = false) String status,
            HttpSession session) {
        
        Map<String, Object> response = new HashMap<>();
        
        // 获取当前登录用户UUID
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            response.put("code", 401);
            response.put("msg", "用户未登录");
            return response;
        }
        
        try {
            // 获取任务列表及总数
            Map<String, Object> pageResult = taskService.getTasksByFamilyId(userId, page, limit, elderName, status);
            
            response.put("code", 0);
            response.put("msg", "success");
            response.put("data", pageResult);
            
            return response;
        } catch (Exception e) {
            response.put("code", 500);
            response.put("msg", "服务器错误: " + e.getMessage());
            return response;
        }
    }
    
    /**
     * 任务详情页面
     */
    @GetMapping("/task-detail/{id}")
    public String showTaskDetailPage(@PathVariable("id") Integer id, Model model, HttpSession session) {
        // 检查用户是否已登录
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }
        
        // 获取任务详情
        Task task = taskService.getTaskById(id);
        
        // 检查任务是否存在且属于当前用户
        if (task != null && taskService.isTaskBelongsToFamily(id, userId)) {
            model.addAttribute("task", task);
        } else {
            model.addAttribute("errorMsg", "任务不存在或您没有权限查看");
        }
        
        return "task/task-detail";
    }
    
    /**
     * 取消任务
     */
    @PostMapping("/cancel/{id}")
    @ResponseBody
    public Map<String, Object> cancelTask(@PathVariable("id") Integer id, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        
        // 获取当前登录用户UUID
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            response.put("code", 401);
            response.put("msg", "用户未登录");
            return response;
        }
        
        try {
            // 检查任务是否属于当前用户
            if (!taskService.isTaskBelongsToFamily(id, userId)) {
                response.put("code", 403);
                response.put("msg", "您没有权限操作此任务");
                return response;
            }
            
            // 取消任务
            boolean result = taskService.deleteTask(userId,id);
            
            if (result) {
                response.put("code", 0);
                response.put("msg", "任务已取消");
            } else {
                response.put("code", 500);
                response.put("msg", "取消任务失败");
            }
            
            return response;
        } catch (Exception e) {
            response.put("code", 500);
            response.put("msg", "服务器错误: " + e.getMessage());
            return response;
        }
    }
    
    /**
     * 创建新任务
     */
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createTask(
            @RequestBody Map<String, Object> requestMap,
            HttpSession session) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前登录用户UUID
            String userId = (String) session.getAttribute("userId");
            if (userId == null) {
                response.put("code", 401);
                response.put("msg", "用户未登录");
                return ResponseEntity.ok(response);
            }
            
            // 从请求体中获取各个字段
            String elderName = (String) requestMap.get("elderName");
            String lostTimeStr = (String) requestMap.get("lostTime");
            String location = (String) requestMap.get("location");
            String photoUrl = (String) requestMap.get("photoUrl");
            String audioUrl = (String) requestMap.get("audioUrl");
            String extraInfo = (String) requestMap.get("extraInfo");
            String status = (String) requestMap.getOrDefault("status", "waiting");
            
            // 参数验证
            if (elderName == null || elderName.trim().isEmpty()) {
                response.put("code", 400);
                response.put("msg", "老人姓名不能为空");
                return ResponseEntity.ok(response);
            }
            
            if (lostTimeStr == null || lostTimeStr.trim().isEmpty()) {
                response.put("code", 400);
                response.put("msg", "走失时间不能为空");
                return ResponseEntity.ok(response);
            }
            
            if (location == null || location.trim().isEmpty()) {
                response.put("code", 400);
                response.put("msg", "走失地点不能为空");
                return ResponseEntity.ok(response);
            }
            
            if (photoUrl == null || photoUrl.trim().isEmpty()) {
                response.put("code", 400);
                response.put("msg", "老人照片不能为空");
                return ResponseEntity.ok(response);
            }
            
            // 转换日期格式
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date lostTime = dateFormat.parse(lostTimeStr);
            
            // 创建Task对象并设置属性
            Task task = new Task();
            task.setElderName(elderName);
            task.setLostTime(lostTime);
            task.setLocation(location);
            task.setPhotoUrl(photoUrl);
            task.setAudioUrl(audioUrl);
            task.setExtraInfo(extraInfo);
            task.setStatus(status);
            
            // 保存任务并关联到用户
            int taskId = taskService.createTaskAndAssignToFamily(task, userId);
            
            response.put("code", 0);
            response.put("msg", "发布成功");
            response.put("data", Map.of("taskId", taskId));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("msg", "服务器错误: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 获取任务详情
     */
    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTaskDetail(@PathVariable("id") Integer id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Task task = taskService.getTaskById(id);
            
            if (task == null) {
                response.put("code", 404);
                response.put("msg", "任务不存在");
                return ResponseEntity.ok(response);
            }
            
            response.put("code", 0);
            response.put("msg", "success");
            response.put("data", task);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("msg", "服务器错误: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 任务编辑页面
     */
    @GetMapping("/task-edit/{id}")
    public String showTaskEditPage(
            @PathVariable("id") Integer id, 
            Model model,
            HttpSession session) {
        
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }
        
        // 获取任务详情
        Task task = taskService.getTaskById(id);
        
        // 验证任务是否属于当前用户
        if (task != null && taskService.isTaskBelongsToFamily(task.getId(), userId)) {
            model.addAttribute("task", task);
            return "task/task-edit";
        } else {
            return "redirect:/task/task-manage";
        }
    }

    /**
     * 更新任务信息
     */
    @PostMapping("/update")
    @ResponseBody
    public Map<String, Object> updateTask(
            @RequestBody Map<String, Object> requestMap,
            HttpSession session) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前登录用户UUID
            String userId = (String) session.getAttribute("userId");
            if (userId == null) {
                response.put("code", 401);
                response.put("msg", "用户未登录");
                return response;
            }
            
            // 获取任务ID
            Integer taskId = Integer.parseInt(requestMap.get("id").toString());
            
            // 检查任务是否属于当前用户
            if (!taskService.isTaskBelongsToFamily(taskId, userId)) {
                response.put("code", 403);
                response.put("msg", "您没有权限操作此任务");
                return response;
            }
            
            // 从请求体中获取各个字段
            String elderName = (String) requestMap.get("elderName");
            String lostTimeStr = (String) requestMap.get("lostTime");
            String location = (String) requestMap.get("location");
            String photoUrl = (String) requestMap.get("photoUrl");
            String audioUrl = (String) requestMap.get("audioUrl");
            String extraInfo = (String) requestMap.get("extraInfo");
            String status = (String) requestMap.get("status");
            
            // 参数验证
            if (elderName == null || elderName.trim().isEmpty()) {
                response.put("code", 400);
                response.put("msg", "老人姓名不能为空");
                return response;
            }
            
            if (lostTimeStr == null || lostTimeStr.trim().isEmpty()) {
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
            
            // 获取原任务信息
            Task existingTask = taskService.getTaskById(taskId);
            if (existingTask == null) {
                response.put("code", 404);
                response.put("msg", "任务不存在");
                return response;
            }
            
            // 转换日期格式
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date lostTime = dateFormat.parse(lostTimeStr);
            
            // 更新Task对象
            existingTask.setElderName(elderName);
            existingTask.setLostTime(lostTime);
            existingTask.setLocation(location);
            existingTask.setPhotoUrl(photoUrl);
            existingTask.setAudioUrl(audioUrl);
            existingTask.setExtraInfo(extraInfo);
            // 不更新状态，使用原状态
            
            // 保存更新后的任务
            boolean success = taskService.updateTask(existingTask);
            
            if (success) {
                response.put("code", 0);
                response.put("msg", "更新成功");
            } else {
                response.put("code", 500);
                response.put("msg", "更新失败");
            }
            
            return response;
        } catch (Exception e) {
            response.put("code", 500);
            response.put("msg", "服务器错误: " + e.getMessage());
            return response;
        }
    }

    /**
     * 智能推荐救援人员
     */
    @GetMapping("/recommend-rescuers/{id}")
    @ResponseBody
    public Map<String, Object> recommendRescuers(
            @PathVariable("id") Integer taskId,
            HttpSession session, HttpServletRequest request) {
         
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前登录用户UUID
            String userId = (String) session.getAttribute("userId");
            if (userId == null) {
                response.put("code", 401);
                response.put("msg", "用户未登录");
                return response;
            }
            
            // 检查任务是否属于当前用户
            if (!taskService.isTaskBelongsToFamily(taskId, userId)) {
                response.put("code", 403);
                response.put("msg", "您没有权限操作此任务");
                return response;
            }
            
            // 获取任务详情
            Task task = taskService.getTaskById(taskId);
            if (task == null) {
                response.put("code", 404);
                response.put("msg", "任务不存在");
                return response;
            }
            
            // 调用服务获取推荐救援人员
            Map<String, Object> recommendationResult = taskService.getRecommendedRescuersForTask(taskId);
            
            if (recommendationResult.containsKey("success") && (Boolean) recommendationResult.get("success")) {
                response.put("code", 0);
                response.put("msg", "获取推荐成功");
                
                // 添加文档下载URL
                String baseUrl = request.getScheme() + "://" + request.getServerName();
                if (request.getServerPort() != 80 && request.getServerPort() != 443) {
                    baseUrl += ":" + request.getServerPort();
                }
                
                // 如果Python服务返回了相对URL，转换为绝对URL
                if (recommendationResult.containsKey("docx_url")) {
                    String docxUrl = (String) recommendationResult.get("docx_url");
                    if (docxUrl.startsWith("/")) {
                        recommendationResult.put("docx_url", baseUrl + docxUrl);
                    }
                }
                
                response.put("data", recommendationResult);
            } else {
                response.put("code", 500);
                response.put("msg", recommendationResult.getOrDefault("message", "获取推荐失败"));
            }
            
            return response;
        } catch (Exception e) {
            response.put("code", 500);
            response.put("msg", "服务器错误: " + e.getMessage());
            return response;
        }
    }

}