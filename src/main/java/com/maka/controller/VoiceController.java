package com.maka.controller;

import com.maka.service.VoiceService;
import com.maka.vo.SearchFeatureResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class VoiceController {

    private final VoiceService voiceService;

    @Autowired
    public VoiceController(VoiceService voiceService) {
        this.voiceService = voiceService;
    }

    @PostMapping("/createFeature")
    public String createFeature(
            @RequestParam String groupId,
            @RequestParam String featureId,
            @RequestParam String featureInfo,
            @RequestParam("audioFile") MultipartFile audioFile) {
        return voiceService.createFeature(groupId, featureId, featureInfo, audioFile);
    }

    @PostMapping("/searchFeature")
    public SearchFeatureResult searchFeature(
            @RequestParam String groupId,
            @RequestParam int topK,
            @RequestParam("audioFile") MultipartFile audioFile) {
        return voiceService.searchFeature(groupId, topK, audioFile);
    }
}
