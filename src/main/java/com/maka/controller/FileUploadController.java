package com.maka.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/upload")
public class FileUploadController {

    /** 本地磁盘存放路径，在 application.yml 里配置 */
    @Value("${file.upload-dir}")
    private String uploadDir;

    /** 外部访问前缀，例如 http://localhost:8080/files/ */
    @Value("${file.access-prefix}")
    private String accessPrefix;

    /** 图片上传 */
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, Object> uploadImage(@RequestPart("file") MultipartFile file) throws Exception {
        return saveFile(file, "img");
    }

    /** 语音上传 */
    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, Object> uploadAudio(@RequestPart("file") MultipartFile file) throws Exception {
        return saveFile(file, "audio");
    }

    /* ======== 通用保存逻辑 ======== */
    private Map<String, Object> saveFile(MultipartFile file, String subDir) throws Exception {
        if (file.isEmpty()) return Map.of("success", false, "msg", "文件为空");

        // 生成唯一文件名
        String ext  = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileName = uuid + "." + ext;

        // 确保目录存在
        Path dirPath = Paths.get(uploadDir, subDir);
        Files.createDirectories(dirPath);

        // 保存到磁盘
        Path savePath = dirPath.resolve(fileName);
        file.transferTo(savePath);

        // 返回可访问的 URL
        String url = accessPrefix + subDir + "/" + fileName;
        return Map.of("success", true, "url", url);
    }
}
