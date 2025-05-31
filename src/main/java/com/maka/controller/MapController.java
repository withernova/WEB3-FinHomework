package com.maka.controller;

import com.maka.pojo.Family;
import com.maka.pojo.Rescuer;
import com.maka.pojo.Task;
import com.maka.service.FamilyService;
import com.maka.service.RescuerService;
import com.maka.service.TaskService;
import com.maka.service.UserService;
import com.maka.service.MapService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import javax.servlet.http.HttpSession;

/**
 * 地图控制器
 */
@Controller
@RequestMapping("/task")
public class MapController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private UserService userService;

    @Autowired
    private FamilyService familyService;

    @Autowired
    private RescuerService rescuerService;

    @Autowired
    private MapService mapService;

    /**
     * 显示任务对应的地图页面
     */
    @GetMapping("/task-map/{taskId}")
    public String showTaskMap(@PathVariable("taskId") Integer taskId, Model model, HttpSession session) {
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
        Task task = taskService.getTaskById(taskId);

        // 检查任务是否存在以及用户是否有权限查看
        boolean hasPermission = false;

        if (task != null) {
            if ("family".equals(userType) && taskService.isTaskBelongsToFamily(taskId, userId)) {
                hasPermission = true;
            } else if ("rescuer".equals(userType)) {
                Rescuer rescuer = rescuerService.getRescuerByUuid(userId);
                if (rescuer != null) {
                    if (rescuer.getTaskIds() != null && rescuer.getTaskIds().contains(taskId)) {
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
            model.addAttribute("userName", userName);
            
            // 尝试获取地图配置，如果不存在，使用默认值
            try {
                Map<String, Object> mapConfig = mapService.getOrCreateMapConfig(taskId);
                model.addAttribute("mapConfig", mapConfig);
            } catch (Exception e) {
                // 记录错误但继续使用默认配置
                System.err.println("无法获取地图配置: " + e.getMessage());
            }
            
            return "task/task-map";
        } else {
            model.addAttribute("errorMsg", "任务不存在或您没有权限查看");
            return "error/403";
        }
    }
}