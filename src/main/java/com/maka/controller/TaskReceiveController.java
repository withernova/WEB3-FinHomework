package com.maka.controller;

import com.maka.pojo.Task;
import com.maka.service.TaskService;
import com.maka.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.*;

/**
 * 面向救援端的"接受任务"控制器
 */
@Controller
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskReceiveController {

    private final TaskService taskService;
    private final UserService userService;  // 添加UserService依赖

    /* ===== 1. 页面 ===== */
    @GetMapping("/task-receive")
    public String receivePage(HttpSession session) {
        // 检查用户是否已登录
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return "common/no-login";
        }
        
        // 检查用户是否为救援者
        if (!userService.isRescuer(userId)) {
            return "common/forbidden-family";  // 返回禁止访问页面
        }
        
        return "task/task-management";
    }

    /* ===== 2. Layui-Table 数据：等待任务优先，其次 rescui ng ===== */
    @GetMapping("/list-data")
    @ResponseBody
    public Map<String,Object> getAllTaskList(@RequestParam(defaultValue="1")  Integer page,
                                             @RequestParam(defaultValue="10") Integer limit,
                                             @RequestParam(required=false)   String elderName,
                                             @RequestParam(required=false)   String location,
                                             @RequestParam(required=false)   String status,
                                             HttpSession session) {

        Map<String,Object> res = new HashMap<>();
        String uid = (String) session.getAttribute("userId");
        if (uid == null) { 
            res.put("code",401); 
            res.put("msg","用户未登录"); 
            return res; 
        }
        
        // 检查用户是否为救援者
        if (!userService.isRescuer(uid)) {
            res.put("code",403); 
            res.put("msg","非救援人员禁止访问"); 
            return res;
        }

        int offset = (page-1)*limit;
        int count  = taskService.countTasks(elderName,location,status);

        /* 统一排序策略已放到 Mapper SQL，通过 ORDER BY CASE 实现 */
        List<Task> rows = taskService.selectTasksByPage(offset,limit,elderName,location,status);

        res.put("code",0); res.put("msg","success"); res.put("count",count); res.put("data",rows);
        return res;
    }

    /* ===== 3. 接受 / 追加 接单 ===== */
    @PostMapping("/accept/{id}")
    @ResponseBody
    public Map<String,Object> accept(@PathVariable Integer id, HttpSession session) {

        Map<String,Object> res = new HashMap<>();
        String uid = (String) session.getAttribute("userId");
        if (uid == null) { 
            res.put("code",401); 
            res.put("msg","用户未登录"); 
            return res; 
        }
        
        // 检查用户是否为救援者
        if (!userService.isRescuer(uid)) {
            res.put("code",403); 
            res.put("msg","非救援人员禁止操作"); 
            return res;
        }

        boolean ok = taskService.acceptTask(id, uid);
        if (ok) { res.put("code",0); res.put("msg","操作成功，任务已加入您的列表"); }
        else   { res.put("code",500); res.put("msg","操作失败，任务可能已完成或数据异常"); }
        return res;
    }

    /* ===== A. "我的任务"页面 ===== */
    @GetMapping("/task-track")
    public String trackPage(HttpSession session) { 
        // 检查用户是否已登录
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return "common/no-login";
        }
        
        // 检查用户是否为救援者
        if (!userService.isRescuer(userId)) {
            return "common/forbidden-family";  // 返回禁止访问页面
        }
        
        return "task/task-track"; 
    }

    /* ===== B. 我的任务数据 ===== */
    @GetMapping("/my-list-data")
    @ResponseBody
    public Map<String,Object> myListData(HttpSession session){

        String uid = (String) session.getAttribute("userId");
        Map<String,Object> res = new HashMap<>();
        if (uid == null) { 
            res.put("code",401); 
            res.put("msg","未登录"); 
            return res; 
        }
        
        // 检查用户是否为救援者
        if (!userService.isRescuer(uid)) {
            res.put("code",403); 
            res.put("msg","非救援人员禁止访问"); 
            return res;
        }

        List<Task> rows = taskService.getTasksByRescuer(uid);
        res.put("code",0); res.put("msg","success");
        res.put("count",rows.size()); res.put("data",rows);
        return res;
    }

    /* ===== C. 完成任务 ===== */
    @PostMapping("/finish/{id}")
    @ResponseBody
    public Map<String,Object> finish(@PathVariable Integer id, HttpSession session) {

        String uid = (String) session.getAttribute("userId");
        Map<String,Object> res = new HashMap<>();
        if (uid == null) { 
            res.put("code",401); 
            res.put("msg","未登录"); 
            return res; 
        }
        
        // 检查用户是否为救援者
        if (!userService.isRescuer(uid)) {
            res.put("code",403); 
            res.put("msg","非救援人员禁止操作"); 
            return res;
        }

        boolean ok = taskService.finishTask(id,uid);
        res.put("code", ok?0:500);
        res.put("msg" , ok? "已标记完成" : "操作失败");
        return res;
    }
}