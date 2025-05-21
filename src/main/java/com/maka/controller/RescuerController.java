package com.maka.controller;

import com.maka.pojo.Rescuer;
import com.maka.pojo.SkillTag;
import com.maka.service.RescuerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;

@Controller
@RequestMapping("/rescuer")
public class RescuerController {

    @Autowired
    private RescuerService rescuerService;
    
    // 跳转到申请页面
    @GetMapping("/apply")
    public String toApplyPage(HttpSession session, Model model) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }
        
        // 检查是否已经申请过
        Rescuer rescuer = rescuerService.getRescuerByUuid(userId);
        if (rescuer != null && !"visitor".equals(rescuer.getStatus())) {
            model.addAttribute("rescuer", rescuer);
            return "rescuer/profile"; // 已存在且状态为available则显示个人资料
        }
        
        // 如果存在但状态不是available，或者不存在，都显示申请表单
        return "rescuer/apply";
    }
    
    // 提交申请
    @PostMapping("/apply")
    @ResponseBody
    public Map<String, Object> applyRescuer(@RequestBody Map<String, String> formData, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        //System.out.println("[applyRescuer] userId from session: " + userId);
        //System.out.println("[applyRescuer] formData: " + formData);

        if (userId == null) {
            //System.out.println("[applyRescuer] userId is null, returning error");
            return Map.of("success", false, "message", "未登录，请重新登录");
        }

        // 创建Rescuer对象并设置属性
        Rescuer rescuer = new Rescuer();
        rescuer.setUuid(userId);
        rescuer.setName(formData.get("name"));
        rescuer.setLocation(formData.get("location"));
        rescuer.setSkillsDescription(formData.get("skillsDescription"));

        // 保存或更新申请信息
        System.out.println("[applyRescuer] Updating or inserting rescuer: " + rescuer);
        boolean result = rescuerService.updateOrInsertRescuer(rescuer);
        
        if (result) {
            return Map.of("success", true, "message", "申请提交成功");
        } else {
            return Map.of("success", false, "message", "申请提交失败");
        }
    }
    
    // 生成技能标签页面
    @GetMapping("/generate-tags")
    public String generateTagsPage(HttpSession session, Model model) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }
        
        Rescuer rescuer = rescuerService.getRescuerByUuid(userId);
        if (rescuer == null) {
            return "redirect:/rescuer/apply";
        }
        
        // 调用服务生成标签
        List<String> generatedTags = rescuerService.generateSkillTags(rescuer.getSkillsDescription());
        
        model.addAttribute("rescuer", rescuer);
        model.addAttribute("generatedTags", generatedTags);
        
        // 获取所有标签
        List<SkillTag> allSkillTags = rescuerService.getAllSkillTags();
        model.addAttribute("allTags", allSkillTags);
        
        return "rescuer/rescuer_tags";
    }
    
    // 保存技能标签
    @PostMapping("/save-tags")
    @ResponseBody
    public Map<String, Object> saveTags(@RequestBody Map<String, Object> params, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return Map.of("success", false, "message", "未登录");
        }

        String name = (String) params.get("name");
        String location = (String) params.get("location");
        String skillsDescription = (String) params.get("skillsDescription");
        @SuppressWarnings("unchecked")
        List<String> skillTags = (List<String>) params.get("tags");

        System.out.println("[saveTags] userId: " + userId);
        System.out.println("[saveTags] 原始 tags: " + params.get("tags"));

        // 过滤掉无意义的tag
        skillTags = skillTags.stream()
            .filter(tag -> !"未找到匹配标签，请优化个人简介".equals(tag))
            .collect(Collectors.toList());

        // 去重，保留顺序
        skillTags = new ArrayList<>(new LinkedHashSet<>(skillTags));

        System.out.println("[saveTags] 过滤&去重后 tags: " + skillTags);

        // 构造Rescuer对象
        Rescuer rescuer = new Rescuer();
        rescuer.setUuid(userId);
        rescuer.setName(name);
        rescuer.setLocation(location);
        rescuer.setSkillsDescription(skillsDescription);
        rescuer.setSkillTags(skillTags); // 你的Rescuer类需有此属性
        rescuer.setStatus("available");

        // 统一调用一个更新方法
        boolean success = rescuerService.updateRescuerWithTags(rescuer);

        if (success) {
            return Map.of("success", true, "message", "信息保存成功");
        } else {
            return Map.of("success", false, "message", "保存失败");
        }
    }
    

    @GetMapping("/check-permission")
    @ResponseBody
    public Map<String, Object> checkPermission(HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return Map.of("code", 401, "msg", "未登录");
        }
        
        // 获取当前用户的搜救队员信息
        Rescuer currentRescuer = rescuerService.getRescuerByUuid(userId);
        
        // 只有状态为available的搜救队员才能查看列表
        if (currentRescuer == null || !"available".equals(currentRescuer.getStatus())) {
            return Map.of("code", 403, "msg", "您需要先申请成为搜救队员");
        }
        
        return Map.of("code", 0, "msg", "有权限访问");
    }

    // 获取所有搜救队员列表
    @GetMapping("/list")
    public String getRescuerList(HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }
        
        // 获取当前用户的搜救队员信息
        Rescuer currentRescuer = rescuerService.getRescuerByUuid(userId);
        
        // 只有状态为available的搜救队员才能查看列表
        if (currentRescuer == null || !"available".equals(currentRescuer.getStatus())) {
            return "redirect:/rescuer/apply";
        }
        
        // 重定向到静态HTML页面
        return "redirect:page/rescuer_list.html";
    }
    @GetMapping("/list-data")
    @ResponseBody
    public Map<String, Object> getRescuerListData(HttpServletRequest request, HttpSession session,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "15") int limit) {
        try {
            // 记录请求信息
            System.out.println("=== /list-data API被调用 ===");
            System.out.println("请求URL: " + request.getRequestURL().toString());
            System.out.println("请求参数: page=" + page + ", limit=" + limit);
            System.out.println("会话ID: " + session.getId());
            
            // 检查用户是否登录
            String userId = (String) session.getAttribute("userId");
            System.out.println("用户ID: " + userId);
            
            if (userId == null) {
                System.out.println("用户未登录，返回错误信息");
                // 注意：返回JSON错误信息，不要重定向
                return Map.of(
                    "code", 401,
                    "msg", "未登录或会话已过期",
                    "count", 0,
                    "data", List.of()
                );
            }
            
            // 获取用户信息
            Rescuer currentRescuer = rescuerService.getRescuerByUuid(userId);
            System.out.println("当前用户: " + (currentRescuer != null ? 
                                "姓名=" + currentRescuer.getName() + 
                                ", 状态=" + currentRescuer.getStatus() : "null"));
            
            // 检查权限
            if (currentRescuer == null || !"available".equals(currentRescuer.getStatus())) {
                System.out.println("用户无权限，状态: " + (currentRescuer != null ? currentRescuer.getStatus() : "null"));
                return Map.of(
                    "code", 403,
                    "msg", "没有访问权限",
                    "count", 0,
                    "data", List.of()
                );
            }
            
            // 分页参数
            int offset = (page - 1) * limit;
            System.out.println("查询参数: offset=" + offset + ", limit=" + limit);
            
            // 查询数据库
            List<Rescuer> rescuers = rescuerService.getAvailableRescuersPaged(offset, limit);
            int total = rescuerService.getAvailableRescuersCount();
            
            System.out.println("查询结果: 总记录数=" + total + ", 当前页记录数=" + 
                            (rescuers != null ? rescuers.size() : "null"));
            
            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("code", 0);
            result.put("msg", "");
            result.put("count", total);
            result.put("data", rescuers);
            
            System.out.println("返回结果: code=0, count=" + total);
            return result;
        } catch (Exception e) {
            System.err.println("API异常: " + e.getMessage());
            e.printStackTrace();
            
            // 返回异常信息
            return Map.of(
                "code", 500,
                "msg", "服务器错误: " + e.getMessage(),
                "count", 0,
                "data", List.of()
            );
        }
    }

        
        // 个人资料页面
    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        String userId = (String) session.getAttribute("userId");
        System.out.println("[profile] userId from session: " + userId);
        
        if (userId == null) {
            System.out.println("[profile] userId is null, redirecting to login");
            return "redirect:/login";
        }
        
        Rescuer rescuer = rescuerService.getRescuerByUuid(userId);
        System.out.println("[profile] rescuer: " + rescuer);
        
        if (rescuer == null) {
            System.out.println("[profile] rescuer is null, redirecting to apply");
            return "redirect:/rescuer/apply";
        }
        
        model.addAttribute("rescuer", rescuer);
        System.out.println("[profile] Model contents: " + model.asMap());
        
        return "rescuer/profile";
    }
    @PostMapping("/profile/save")
    public String saveProfile(@RequestParam Map<String, String> params, HttpSession session, Model model) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        Rescuer rescuer = rescuerService.getRescuerByUuid(userId);
        if (rescuer == null) {
            return "redirect:/rescuer/apply";
        }

        // 更新字段
        rescuer.setName(params.get("name"));
        rescuer.setLocation(params.get("location"));
        rescuer.setSkillsDescription(params.get("skillsDescription"));
        // skillTags 不改，status建议保持原值

        boolean success = rescuerService.updateOrInsertRescuer(rescuer);
        if (success) {
            model.addAttribute("rescuer", rescuerService.getRescuerByUuid(userId));
            model.addAttribute("msg", "保存成功！");
        } else {
            model.addAttribute("rescuer", rescuer);
            model.addAttribute("msg", "保存失败，请重试。");
        }
        return "rescuer/profile";
    }
}