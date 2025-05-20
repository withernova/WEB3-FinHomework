package com.maka.controller;

import com.maka.pojo.Rescuer;
import com.maka.pojo.SkillTag;
import com.maka.service.RescuerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

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
        if (rescuer != null && "available".equals(rescuer.getStatus())) {
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
        System.out.println("[applyRescuer] userId from session: " + userId);
        System.out.println("[applyRescuer] formData: " + formData);

        if (userId == null) {
            System.out.println("[applyRescuer] userId is null, returning error");
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
        
        return "rescuer/tags";
    }
    
    // 保存技能标签
    @PostMapping("/save-tags")
    @ResponseBody
    public Map<String, Object> saveTags(@RequestBody Map<String, Object> params, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return Map.of("success", false, "message", "未登录");
        }
        
        @SuppressWarnings("unchecked")
        List<String> skillTags = (List<String>) params.get("tags");
        
        boolean success = rescuerService.updateRescuerSkillTags(userId, skillTags);
        
        if (success) {
            return Map.of("success", true, "message", "技能标签保存成功");
        } else {
            return Map.of("success", false, "message", "技能标签保存失败");
        }
    }
    
    // 获取所有搜救队员列表
    @GetMapping("/list")
    public String getRescuerList(HttpSession session, Model model) {
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
        
        List<Rescuer> rescuers = rescuerService.getAvailableRescuers();
        model.addAttribute("rescuers", rescuers);
        
        return "rescuer/list";
    }
    
    // 个人资料页面
    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }
        
        Rescuer rescuer = rescuerService.getRescuerByUuid(userId);
        if (rescuer == null) {
            return "redirect:/rescuer/apply";
        }
        
        model.addAttribute("rescuer", rescuer);
        return "rescuer/profile";
    }
}