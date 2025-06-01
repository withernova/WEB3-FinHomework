package com.maka.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maka.pojo.Family;
import com.maka.pojo.Rescuer;
import com.maka.pojo.Task;
import com.maka.pojo.TaskMessage;
import com.maka.service.TaskService;
import com.maka.service.UserService;
import com.maka.service.FamilyService;
import com.maka.service.RescuerService;
import com.maka.service.TaskMessageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest; // 别忘了导包
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

    @Autowired
    private UserService userService;

    @Autowired
    private FamilyService familyService;

    @Autowired
    private RescuerService rescuerService;

    @Autowired
    private TaskMessageService taskMessageService;


    @GetMapping("/task-publish")
    public String showTaskPublishPage(HttpSession session) {
        // 检查用户是否已登录
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            // 如果未登录，重定向到登录页面
            return "common/no-login";
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
            return "common/no-login";
        }
        
        // 返回任务管理页面
        return "task/task-manage";
    }

    /**
     * 显示任务接收页面
     */
    @GetMapping("/task-management")
    public String showTaskManagementPage(HttpSession session) {
        // 检查用户是否已登录
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return "common/no-login";
        
        }
        // 检查用户是否为救援者
        if (!userService.isRescuer(userId)) {
            // 如果不是救援者，重定向到适当的页面
            return "redirect:/task/task-manage";
        }
        
        return "task/task-management";
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
            return "common/no-login";
        }
        
        // 获取用户类型
        String userType = userService.getUserType(userId);
        
        // 获取用户名
        String userName = "";
        if ("rescuer".equals(userType)) {
            Rescuer rescuer = rescuerService.getRescuerByUuid(userId);
            if (rescuer != null) {
                userName = rescuer.getName();
            }
        } else if ("family".equals(userType)) {
            Family family = familyService.getFamilyByUuid(userId);
            if (family != null) {
                userName = family.getName();
            }
        }
        
        // 获取任务详情
        Task task = taskService.getTaskById(id);
        
        // 检查任务是否存在以及用户是否有权限查看
        boolean hasPermission = false;
        
        if (task != null) {
            if ("family".equals(userType) && taskService.isTaskBelongsToFamily(id, userId)) {
                hasPermission = true;
            } else if ("rescuer".equals(userType)) {
                Rescuer rescuer = rescuerService.getRescuerByUuid(userId);
                if (rescuer != null) {
                    if (rescuer.getTaskIds() != null && rescuer.getTaskIds().contains(id)) {
                        hasPermission = true;
                    } else if ("available".equals(rescuer.getStatus()) && "waiting".equals(task.getStatus())) {
                        // 可用的救援者可以查看等待中的任务
                        hasPermission = true;
                    }
                }
            }
        }
        
        if (hasPermission) {
            model.addAttribute("task", task);
            model.addAttribute("userType", userType);
            model.addAttribute("userName", userName); // 添加用户名到模型中
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
            return "common/no-login";
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
                    System.out.println("原始docx_url: " + docxUrl);
                    
                    // 设置Python服务的基础URL
                    String pythonServiceUrl = "http://127.0.0.1:5000"; // 修改为您的Python服务URL
                    
                    if (docxUrl.startsWith("/")) {
                        // 使用Python服务URL而不是Java服务URL
                        docxUrl = pythonServiceUrl + docxUrl;
                        System.out.println("修正后的docx_url: " + docxUrl);
                        recommendationResult.put("docx_url", docxUrl);
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

    @PostMapping("/generate-summary")
    @ResponseBody
    public Map<String, Object> generateSummary(
            @RequestBody Map<String, Object> templateData,
            HttpSession session) {
        
        // 获取当前登录用户UUID
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 401);
            response.put("msg", "用户未登录");
            return response;
        }
        
        
        // 调用服务生成摘要
        return taskService.generateElderInfoSummary(templateData);
    }


    /**
     * 获取任务消息
    */
    @GetMapping("/messages/{id}")
    @ResponseBody
    public Map<String, Object> getTaskMessages(
            @PathVariable("id") Integer id,
            @RequestParam(required = false) Integer before,
            @RequestParam(required = false) Integer after,
            @RequestParam(defaultValue = "50") Integer limit,
            HttpSession session) {
        
        Map<String, Object> result = new HashMap<>();
        
        // 检查用户是否已登录
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            result.put("code", 401);
            result.put("msg", "用户未登录");
            return result;
        }
        
        // 获取用户类型
        String userType = userService.getUserType(userId);
        
        // 检查任务是否存在以及用户是否有权限查看
        Task task = taskService.getTaskById(id);
        boolean hasPermission = false;
        
        if (task != null) {
            if ("family".equals(userType) && taskService.isTaskBelongsToFamily(id, userId)) {
                hasPermission = true;
            } else if ("rescuer".equals(userType)) {
                Rescuer rescuer = rescuerService.getRescuerByUuid(userId);
                if (rescuer != null && rescuer.getTaskIds() != null) {
                    hasPermission = rescuer.getTaskIds().contains(id);
                }
            }
        }
        
        if (!hasPermission) {
            result.put("code", 403);
            result.put("msg", "无权限查看此任务");
            return result;
        }
        
        try {
            List<TaskMessage> messages;
            if (after != null) {
                messages = taskMessageService.getMessagesAfter(id, after, limit);
            } else if (before != null) {
                messages = taskMessageService.getMessagesBefore(id, before, limit);
            } else {
                messages = taskMessageService.getMessages(id, limit);
            }
            if (messages == null) messages = new ArrayList<>();

            Integer latestId = taskMessageService.getLatestMessageId(id);

            Map<String, Object> data = new HashMap<>();
            data.put("messages", messages);
            data.put("latestId", latestId); // 可以允许 null
            data.put("hasMore", messages.size() >= limit);

            result.put("code", 0);
            result.put("msg", "");
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "服务器错误: " + (e != null ? e.getMessage() : "未知异常"));
        }
                
        return result;
    }

    /**
     * 发送任务消息
     */
    @PostMapping("/send-message")
    @ResponseBody
    public Map<String, Object> sendTaskMessage(@RequestBody Map<String, Object> messageData, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        
        // 检查用户是否已登录
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            result.put("code", 401);
            result.put("msg", "用户未登录");
            return result;
        }
        
        // 获取任务ID和消息数据
        Integer taskId = (Integer) messageData.get("taskId");
        String msgDataStr = (String) messageData.get("messageData");
        
        if (taskId == null || msgDataStr == null) {
            result.put("code", 400);
            result.put("msg", "参数错误");
            return result;
        }
        
        // 获取用户类型和用户名
        String userType = userService.getUserType(userId);
        String userName = "";
        if ("rescuer".equals(userType)) {
            Rescuer rescuer = rescuerService.getRescuerByUuid(userId);
            if (rescuer != null) {
                userName = rescuer.getName();
            }
        } else if ("family".equals(userType)) {
            Family family = familyService.getFamilyByUuid(userId);
            if (family != null) {
                userName = family.getName();
            }
        }
        
        try {
            // 解析前端发送的消息数据
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> msgData = mapper.readValue(msgDataStr, Map.class);
            
            // 修正消息数据中的发送者信息
            Map<String, Object> sender = (Map<String, Object>) msgData.get("sender");
            if (sender != null) {
                sender.put("id", userId);      // 使用实际的用户ID
                sender.put("name", userName);  // 使用实际的用户名
                sender.put("type", userType);  // 使用实际的用户类型
            }
            
            // 重新序列化消息数据
            String correctedMsgData = mapper.writeValueAsString(msgData);
            
            // 检查权限...
            Task task = taskService.getTaskById(taskId);
            boolean hasPermission = false;
            
            if (task != null && "rescuing".equals(task.getStatus())) {
                if ("family".equals(userType) && taskService.isTaskBelongsToFamily(taskId, userId)) {
                    hasPermission = true;
                } else if ("rescuer".equals(userType)) {
                    Rescuer rescuer = rescuerService.getRescuerByUuid(userId);
                    if (rescuer != null && rescuer.getTaskIds() != null) {
                        hasPermission = rescuer.getTaskIds().contains(taskId);
                    }
                }
            }
            
            if (!hasPermission) {
                result.put("code", 403);
                result.put("msg", "无权限发送消息或任务状态不允许发送消息");
                return result;
            }
            
            // 保存修正后的消息
            TaskMessage message = taskMessageService.saveMessage(taskId, correctedMsgData);
            
            result.put("code", 0);
            result.put("msg", "发送成功");
            result.put("data", Map.of("messageId", message.getId()));
            
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "服务器错误: " + e.getMessage());
        }
        
        return result;
    }
}