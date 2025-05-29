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
    public Map<String, Object> getAllTaskList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(required = false) String elderName,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String status
    ) {
        int offset = (page - 1) * limit;
        int count = taskService.countTasks(elderName, location, status);
        List<Task> data = taskService.selectTasksByPage(offset, limit, elderName, location, status);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("msg", "");
        result.put("count", count);
        result.put("data", data);

        return result;
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

    /* ========= A. 进入‘任务追踪’页面 ========= */
    @GetMapping("/task-track")
    public String trackPage() {
        return "task/task-track";     // 对应下面的 HTML
    }

    /* ========= B. Layui 表格数据：我的任务 ========= */
    @GetMapping("/my-list-data")
    @ResponseBody
    public Map<String,Object> myListData(HttpSession session) {

        String rescuerUid = (String) session.getAttribute("userId");
        Map<String,Object> res = new HashMap<>();
        if (rescuerUid == null) {
            res.put("code",401); res.put("msg","未登录"); return res;
        }

        List<Task> rows  = taskService.getTasksByRescuer(rescuerUid); // 新增 service
        res.put("code",0);
        res.put("msg","success");
        res.put("count", rows.size());
        res.put("data", rows);
        return res;
    }

    /* ========= C. 完成任务 ========= */
    @PostMapping("/finish/{id}")
    @ResponseBody
    public Map<String,Object> finish(@PathVariable Integer id,
                                    HttpSession session){

        String rescuerUid = (String) session.getAttribute("userId");
        Map<String,Object> res = new HashMap<>();
        if (rescuerUid == null) {
            res.put("code",401); res.put("msg","未登录"); return res;
        }

        // 只允许自己的任务改状态
        boolean ok = taskService.finishTask(id, rescuerUid);
        if (ok) { res.put("code",0);   res.put("msg","已标记完成"); }
        else    { res.put("code",500); res.put("msg","操作失败"); }
        return res;
    }
}
