package com.maka.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maka.pojo.Task;
import com.maka.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/task")
public class TaskController {

    @Autowired private TaskService taskService;

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper       OM  = new ObjectMapper();

    // ========================================================================
    // 发布接口
    // ========================================================================
    @PostMapping("/publish")
    @ResponseBody
    public Map<String,Object> publish(@RequestBody Map<String,String> form,
                                      HttpSession session){

        String userId = String.valueOf(session.getAttribute("userId"));
        if(userId==null || "null".equals(userId)){
            return Map.of("success",false,"message","未登录，请重新登录");
        }

        try{
            /* ---------- 1. 组装实体并入库 ---------- */
            Task task = new Task();
            task.setElderName(form.get("lost_person_name"));

            try{
                task.setLostTime(SDF.parse(form.get("lost_time")));
            }catch (ParseException e){
                return Map.of("success",false,"message","时间格式必须为 yyyy-MM-dd HH:mm:ss");
            }

            task.setPhotoUrl(form.get("photo_url"));
            task.setAudioUrl(form.get("audio_url"));
            String lostAddress = form.get("lost_province")+
                                 form.get("lost_city")+
                                 form.get("lost_area")+
                                 form.getOrDefault("specific_address","");
            task.setLocation(lostAddress);
            task.setStatus("waiting");

            Map<String,String> extra = new HashMap<>();
            extra.put("publisher",   userId);
            extra.put("contactPhone",form.get("contact_phone"));
            task.setExtraInfo(OM.writeValueAsString(extra));

            taskService.createTask(task);         // 回填主键
            int taskId = task.getId();

            /* ---------- 2. 生成占位文件 ---------- */
            String reportsDir = System.getProperty("user.dir")
                                 + File.separator+"uploads"+File.separator+"reports";
            File dir = new File(reportsDir);
            if(!dir.exists()) dir.mkdirs();

            String fileName   = "report_"+taskId+".txt";
            File   reportFile = new File(dir, fileName);
            if(!reportFile.exists()){
                writeTxtWithBom(reportFile, "报告生成中，请稍后刷新下载……");
            }

            /* ---------- 3. 同步请求 Python ---------- */
            String report = callPythonAndGetReport(lostAddress);
            if(report != null && !report.isBlank()){
                writeTxtWithBom(reportFile, report); // 覆盖占位
                log.info("已写入最终报告：{}", reportFile.getAbsolutePath());
            }else{
                log.warn("Python 返回空报告，文件保持占位状态");
            }

            /* ---------- 4. 返回结果 ---------- */
            return Map.of(
                    "success",   true,
                    "reportUrl", "/task/downloadReport/"+fileName
            );

        }catch (Exception ex){
            log.error("发布任务异常", ex);
            return Map.of("success",false,"message","服务器内部错误");
        }
    }

    // ========================================================================
    // 私有工具
    // ========================================================================
    /** 把字符串写入 TXT（UTF-8+BOM，防止 Windows 记事本乱码） */
    private void writeTxtWithBom(File file, String content) throws Exception{
        byte[] bom  = {(byte)0xEF,(byte)0xBB,(byte)0xBF};
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        try(FileOutputStream fos = new FileOutputStream(file,false)){
            fos.write(bom);
            fos.write(body);
        }
    }

    /** 向 Python 发 POST，拿回 report 字段 */
    private String callPythonAndGetReport(String lostAddress){
        RestTemplate rest = null;
        try{
            // ① 设置 5 秒超时
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout   (15000);
            rest = new RestTemplate(factory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String,Object> payload = new HashMap<>();
            payload.put("family_name",  "");
            payload.put("lost_address", lostAddress);
            payload.put("needed_tags",  new String[0]);

            log.info("向 Python 发起请求: {}", payload);     // DEBUG 1
            ResponseEntity<String> resp = rest.postForEntity(
                    "http://127.0.0.1:5000/api/recommend_volunteers",
                    new HttpEntity<>(payload, headers),
                    String.class);

            log.info("收到 Python 状态码: {}", resp.getStatusCodeValue()); // DEBUG 2
            log.debug("收到 Python 响应体: {}", resp.getBody());

            if(resp.getStatusCode().is2xxSuccessful() && resp.getBody()!=null){
                JsonNode root = OM.readTree(resp.getBody());
                return root.path("report").asText(null);
            }
        }catch (Exception e){
            log.error("调用 Python 失败：{}", e.toString());
            if(rest != null){
                e.printStackTrace();
            }
        }
        return null;
    }

    // ========================================================================
    // 自定义下载接口（带 charset）
    // ========================================================================
    @GetMapping("/downloadReport/{fileName:.+}")
    public ResponseEntity<Resource> downloadReport(@PathVariable String fileName){
        try{
            Path path = Path.of(System.getProperty("user.dir"),
                                "uploads","reports",fileName);
            Resource resource = new UrlResource(path.toUri());
            if(!resource.exists()) return ResponseEntity.notFound().build();

            return ResponseEntity.ok()
                    .contentType(new MediaType("text","plain", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\""+fileName+"\"")
                    .body(resource);

        }catch (MalformedURLException e){
            return ResponseEntity.badRequest().build();
        }
    }
}
