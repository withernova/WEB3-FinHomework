package com.maka.controller;

import com.maka.service.FaceComparisonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/face")
public class FaceComparisonController {

    @Autowired
    private FaceComparisonService faceService;

    /* ---------- 1. 照片质量检测 ---------- */
    @PostMapping("/checkQuality")
    public ResponseEntity<?> checkQuality(@RequestParam("file") MultipartFile file) {
        Map<String,Object> resp = new HashMap<>();
        try {
            if (file.isEmpty()) {
                resp.put("qualified", false);
                resp.put("msg", "文件为空");
                return ResponseEntity.ok(resp);
            }
            boolean ok = faceService.checkQuality(file);
            resp.put("qualified", ok);
            resp.put("msg", ok ? "合格" : "照片模糊 / 遮挡");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {                       // 把所有质量异常也当业务返回
            resp.put("qualified", false);
            resp.put("msg", "API Error: " + e.getMessage());
            return ResponseEntity.ok(resp);           // 依旧 200
        }
    }

    /* ---------- 2. 照片比对 ---------- */
    @PostMapping("/compare")
    public ResponseEntity<String> compare(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.ok("{\"lostPerson\":\"无\"}");
            }
            String resultJson = faceService.compareFace(file);   // 正常 JSON 字符串
            return ResponseEntity.ok(resultJson);
        } catch (Exception e) {                                  // 比对失败也 200
            return ResponseEntity.ok("{\"lostPerson\":\"无\"}");
        }
    }

    /* ---------- 3. 视频比对 ---------- */
    @PostMapping("/compareVideo")
    public ResponseEntity<String> compareVideo(@RequestParam("videoFile") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.ok("{\"lostPerson\":\"无\"}");
            }
            String resultJson = faceService.compareFaceFromVideo(file);
            return ResponseEntity.ok(resultJson);
        } catch (Exception e) {
            return ResponseEntity.ok("{\"lostPerson\":\"无\"}");
        }
    }
}
