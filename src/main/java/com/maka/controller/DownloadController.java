package com.maka.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * 统一下载 TXT 报告 —— Content-Type: text/plain; charset=utf-8
 */
@Slf4j
@RestController
public class DownloadController {

    @Value("${file.report-dir:E:/26654/Documents/WEB3-FinHomework/uploads/reports}")
    private String reportDir;

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(String file) throws IOException {

        if (file == null || file.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        Path path = Paths.get(reportDir, file).normalize();
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        byte[] data = Files.readAllBytes(path);

        /* -------- HTTP Header -------- */
        HttpHeaders headers = new HttpHeaders();

        // Content-Disposition（带 UTF-8 文件名）
        String encoded = URLEncoder.encode(file, StandardCharsets.UTF_8)
                                   .replaceAll("\\+", "%20");
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded);

        headers.setContentType(new MediaType("text", "plain", StandardCharsets.UTF_8));
        headers.setContentLength(data.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(new ByteArrayInputStream(data)));
    }
}
