package com.maka.controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import com.maka.service.FaceComparisonService;
@RestController
public class FaceComparisonController {

    @Autowired
    private FaceComparisonService faceRegService;

    @PostMapping("/faceCompare")
    public ResponseEntity<?> compareFaces(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.status(400).body("File is missing or invalid.");
            }
            // 调用服务处理图片
            String result = faceRegService.compareFace(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Comparison failed.");
        }
    }

    @PostMapping("/faceCompare_video")
    public ResponseEntity<?> compareFacesFromVideo(@RequestParam("videoFile") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.status(400).body("File is missing or invalid.");
            }
            String result = faceRegService.compareFaceFromVideo(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Comparison failed.");
        }
    }
}
