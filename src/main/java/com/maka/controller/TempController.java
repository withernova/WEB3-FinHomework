package com.maka.controller;

import com.maka.service.VoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TempController {

    private final VoiceService voiceService;

    @Autowired
    public TempController(VoiceService voiceService) {
        this.voiceService = voiceService;
    }

    @GetMapping("/temp/createGroup")
    public String tempCreateGroup() {
        String groupId = voiceService.createGroup();
        return "创建的特征组ID为: " + groupId + " 请记录下来并在前端代码中使用此ID";
    }
}