package com.maka.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SceneTrainController {

    @GetMapping("/rescuer/scene_train")
    public String sceneTrain(Model model) {
        // 后端只负责渲染模板，API 交互全走 Flask
        model.addAttribute("apiUrl", "/api/scene-train");
        return "rescuer/scene_train";
    }
}
