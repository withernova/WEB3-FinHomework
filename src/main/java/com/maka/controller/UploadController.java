package com.maka.controller;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/upload")
public class UploadController {

    /** 注入文件根目录，例如  src/main/resources/static/rescue_informaition */
    @Value("${file.upload-dir}")
    private String baseDir;

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /* -------- 图片上传 -------- */
    @PostMapping("/image")
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file) throws Exception {
        return saveFile(file, "image");
    }

    /* -------- 语音上传 -------- */
    @PostMapping("/audio")
    public Map<String, Object> uploadAudio(@RequestParam("file") MultipartFile file) throws Exception {
        return saveFile(file, "audio");
    }

    /* ======== 通用保存逻辑 ======== */
    private Map<String, Object> saveFile(MultipartFile file, String subDir) throws Exception {

        // 1) 生成文件名：时间戳 + uuid + 扩展名
        String ext   = FilenameUtils.getExtension(file.getOriginalFilename());
        String fname = TS_FMT.format(LocalDateTime.now()) + "_" +
                       UUID.randomUUID().toString().substring(0, 8) + "." + ext;

        // 2) 计算保存路径
        Path saveDir  = Paths.get(baseDir, subDir);
        Files.createDirectories(saveDir);
        Path savePath = saveDir.resolve(fname);

        // 3) 写入硬盘
        FileCopyUtils.copy(file.getInputStream(), Files.newOutputStream(savePath));

        // 4) 返回给前端 / 数据库的相对 URL
        String relativeUrl = "/rescue_informaition/" + subDir + "/" + fname;

        Map<String, Object> res = new HashMap<>();
        res.put("code", 0);
        res.put("url" , relativeUrl);   // 前端直接拿来展示 & 保存数据库
        return res;
    }
}
