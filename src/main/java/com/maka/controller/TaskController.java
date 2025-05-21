package com.maka.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maka.pojo.Task;
import com.maka.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpSession;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 任务控制器
 */
@Slf4j
@Controller
@RequestMapping("/task")
public class TaskController {

    @Autowired
    private TaskService taskService;

    /** Python 服务地址，可写到 application.yml */
    @Value("${python.server-url:http://127.0.0.1:5000}")
    private String pythonServerUrl;

    /** 报告文件根目录 */
    @Value("${report.base-dir:E:/26654/Documents/WEB3-FinHomework/uploads/reports}")
    private String reportBaseDir;

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper      OM  = new ObjectMapper();

    /* ------------------------------------------------------------------ */
    /** 任务发布 */
    @PostMapping("/publish")
    @ResponseBody
    public Map<String,Object> publishTask(@RequestBody Map<String, String> form,
                                          HttpSession session){

        String userId = (String) session.getAttribute("userId");
        if (userId == null){
            return Map.of("success",false,"message","未登录，请重新登录");
        }

        try{
            /* 1. 组装 Task 保存数据库 --------------------------------------------------- */
            Task task = new Task();
            task.setElderName(form.get("lost_person_name"));

            Date lostDate;
            try{
                lostDate = SDF.parse(form.get("lost_time"));
            }catch (ParseException e){
                return Map.of("success",false,"message","走失时间格式不正确，形如 2025-05-21 12:00:00");
            }
            task.setLostTime(lostDate);

            task.setPhotoUrl(form.get("photo_url"));
            task.setAudioUrl(form.get("audio_url"));
            task.setLocation(form.get("lost_province")
                            + form.get("lost_city")
                            + form.get("lost_area")
                            + form.getOrDefault("specific_address",""));

            task.setStatus("waiting");

            Map<String,String> extra = Map.of(
                    "publisher", userId,
                    "contactPhone", form.get("contact_phone")
            );
            task.setExtraInfo(OM.writeValueAsString(extra));

            taskService.createTask(task);

            /* 2. 调用 Python 获取报告正文 ---------------------------------------------- */
            String reportTxt = callPythonAndGetReport(task);

            /* 3. 写 TXT（UTF-8） ------------------------------------------------------- */
            String fileName = "report_" + UUID.randomUUID() + ".txt";
            Path   dirPath  = Paths.get(reportBaseDir);
            Files.createDirectories(dirPath);

            Path filePath = dirPath.resolve(fileName);
            Files.writeString(filePath, reportTxt, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            /* 4. 返回可下载 URL -------------------------------------------------------- */
            String url = "/task/download/" + fileName;
            return Map.of("success",true,"reportUrl",url);

        }catch (Exception e){
            log.error("发布任务异常", e);
            return Map.of("success",false,"message","服务器错误");
        }
    }

    /* ------------------------------------------------------------------ */
    /** 下载 TXT —— 带 charset=utf-8 响应头，避免乱码 */
    @GetMapping("/download/{fileName:.+\\.txt}")
    public ResponseEntity<InputStreamResource> downloadReport(@PathVariable String fileName) {

        // 1. 防止路径穿越
        if (fileName.contains("..")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Path file = Paths.get(reportBaseDir, fileName).normalize();
        if (!Files.exists(file)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            byte[] data = Files.readAllBytes(file);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("text", "plain", StandardCharsets.UTF_8));

            // Content-Disposition 处理中文文件名
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                                       .replaceAll("\\+", "%20");
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''" + encoded);

            headers.setContentLength(data.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(new ByteArrayInputStream(data)));

        } catch (IOException e) {
            log.error("下载报告失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /* ------------------------------------------------------------------ */
    /** 调用 Flask 接口 */
    private String callPythonAndGetReport(Task task) {

        try{
            RestTemplate rest = new RestTemplate();

            Map<String,Object> body = Map.of("lost_address", task.getLocation());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Object> req = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map> resp = rest.postForEntity(
                    pythonServerUrl + "/api/recommend_volunteers",
                    req, Map.class);

            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                return (String) resp.getBody()
                                    .getOrDefault("report", "报告生成失败，请稍后重试。");
            }
        }catch (Exception ex){
            log.error("调用 Python 服务失败", ex);
        }
        return "报告生成失败，请稍后重试。";
    }
}
