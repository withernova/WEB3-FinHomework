package com.maka.controller;

import com.maka.pojo.Task;
import com.maka.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面向救援端的“接受任务”控制器
 */
@Controller
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskReceiveController {

    private final TaskService taskService;

    /* ========= 1. 进入页面 ========= */
    @GetMapping("/task-receive")
    public String receivePage() {
        return "task/task-management";
    }

    /* ========= 2. Layui 表格数据 ========= */
    @GetMapping("/list-data")
    @ResponseBody
    public Map<String, Object> listData(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "15") int limit) {

        List<Task> rows = taskService.getTasksByStatus("waiting");
        int total      = rows.size();

        Map<String, Object> res = new HashMap<>();
        res.put("code", 0);
        res.put("msg" , "success");
        res.put("count", total);
        res.put("data", rows);
        return res;
    }

    /* ========= 3. 接受任务 ========= */
    @PostMapping("/accept/{id}")
    @ResponseBody
    public Map<String, Object> accept(@PathVariable Integer id,
                                      HttpSession session) {

        Map<String, Object> res = new HashMap<>();

        // 与 createTask 保持一致 —— 从 Session 取当前登录用户 uuid
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            res.put("code", 401);
            res.put("msg" , "用户未登录");
            return res;
        }

        boolean ok = taskService.acceptTask(id, userId);

        if (ok) {
            res.put("code", 0);
            res.put("msg" , "已接受");
        } else {
            res.put("code", 500);
            res.put("msg" , "操作失败，任务可能已被领取或不存在");
        }
        return res;
    }
}
