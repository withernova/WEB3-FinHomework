package com.maka.service.impl;

import cn.hutool.http.useragent.UserAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maka.service.FaceComparisonService;
import com.baidu.aip.face.AipFace;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.bytedeco.javacv.Frame;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;

@Service
public class FaceComparisonServiceImpl implements FaceComparisonService {
    // 初始化百度API客户端
    private static final String APP_ID = "115865410";
    private static final String API_KEY = "VNSPEBw6uuHFjLqgV4w9WsnK";
    private static final String SECRET_KEY = "G2X1OaeqIgO6umffm5kdGTJuRIQZj5iB";
    private static final AipFace client = new AipFace(APP_ID, API_KEY, SECRET_KEY);

    @Override
    public boolean checkQuality(MultipartFile file) throws Exception {
        // 将图片文件转换为Base64编码
        byte[] bytes = file.getBytes();
        String imgStr = Base64.getEncoder().encodeToString(bytes);

        HashMap<String, String> options = new HashMap<>();
        options.put("image_type", "BASE64");
        options.put("liveness_control", "LOW");
        options.put("quality_control", "NORMAL");
        options.put("action_type", "REPLACE");

        JSONObject response = client.addUser(imgStr, "BASE64","lost_people", "quality_check", options);
        if (response.getInt("error_code") == 0) {
            return true;
        }
        else if(response.getInt("error_code") == 222202 || response.getInt("error_code") == 222203){
            return false;
        }
        throw new Exception("API Error: " + response.getString("error_msg"));
    }

    @Override
    public void addFace(MultipartFile file, String userId, String userName) throws Exception{
        // 将图片文件转换为Base64编码
        byte[] bytes = file.getBytes();
        String imgStr = Base64.getEncoder().encodeToString(bytes);

        HashMap<String, String> options = new HashMap<>();
        options.put("image_type", "BASE64");
        options.put("action_type", "APPEND");
        options.put("user_info", userName);

        JSONObject response = client.addUser(imgStr, "BASE64","lost_people", userId, options);
        if (response.getInt("error_code") != 0) {
            throw new Exception("API Error: " + response.getString("error_msg"));
        }
    }
    @Override
    public void deleteUser(String userId) throws Exception{
        HashMap<String, String> options = new HashMap<>();
        JSONObject response = client.deleteUser(userId,"lost_people", options);
        if (response.getInt("error_code") != 0) {
            throw new Exception("API Error: " + response.getString("error_msg"));
        }
    }

    @Override
    public String compareFace(MultipartFile file) throws Exception {
        // 将图片文件转换为Base64编码
        byte[] bytes = file.getBytes();
        String imgStr = Base64.getEncoder().encodeToString(bytes);

        // 设置百度API参数
        HashMap<String, Object> options = new HashMap<>();
        options.put("image_type", "BASE64");
        options.put("liveness_control", "LOW");
        options.put("match_threshold", "50");

        // 调用百度API进行人脸比对
        JSONObject response = client.search(imgStr, "BASE64","lost_people", options);
        if (response.getInt("error_code") == 0) {
            // 处理比对结果
            JSONObject result = response.getJSONObject("result").getJSONArray("user_list").getJSONObject(0);
            String uid = result.getString("user_id");
            String name = result.getString("user_info");
            double similarity = result.getDouble("score");

            File jsonFile = new File("src/main/resources/static/data/table.json");
            ObjectMapper objectMapper = new ObjectMapper();

            //TODO:改为从数据库获取照片
            Map<String, Object> table = objectMapper.readValue(jsonFile, Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>)table.get("data");

            String img = "";
            for (Map<String, Object> datum: data)
            {
                if (Objects.equals(datum.get("uid"), uid))
                {
                    name = (String)datum.get("oldName");
                    img = (String) datum.get("img");
                }
            }
            return "{" +
                    "\"lostPerson\": \"" + name + "\"," +
                    "\"similarity\": " + similarity + ","+
                    "\"img\": \"" + img + "\"" +
                    "}";
        } else {
            if(response.getInt("error_code") == 222207)
                return "{" +
                        "\"lostPerson\": \"" + "无" + "\"" +
                        "}";
            throw new Exception("API Error: " + response.getString("error_msg"));
        }
    }


    @Override
    public String compareFaceFromVideo(MultipartFile file) throws Exception{
        String videoPath = null;
        String frameDir = "frames/";
        // 设置百度API参数
        HashMap<String, Object> options = new HashMap<>();
        options.put("image_type", "BASE64");
        options.put("liveness_control", "LOW");
        options.put("match_threshold", "50");

        // 隔stepSecond秒抽一帧
        int stepSecond = 1;
        // 超过多少秒停止抽帧
        int timeout = 10;
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file.getInputStream());
        grabber.setOption("timeout", "" + timeout * 1000000);

        grabber.start();
        long timeLength = grabber.getLengthInTime();
        Frame frame = grabber.grabImage();
        long startTime = frame.timestamp;
        long timestamp = 0;
        int second = 0;
        int picNum = 0;

        String uid = "无";
        double similarity = -1;
        String bestFrame = "";
        while(timestamp <= timeLength)
        {
            timestamp = startTime + second * 1000000L;
            grabber.setTimestamp(timestamp);
            frame = grabber.grabImage();
            if(frame != null && frame.image != null)
            {
                picNum++;
                Java2DFrameConverter converter = new Java2DFrameConverter();
                BufferedImage image = converter.convert(frame);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                byte[] bytes = baos.toByteArray();
                String imgStr = Base64.getEncoder().encodeToString(bytes);
                JSONObject response = client.search(imgStr, "BASE64","lost_people", options);
                if (response.getInt("error_code") == 0) {
                    JSONObject result = response.getJSONObject("result").getJSONArray("user_list").getJSONObject(0);
                    double score = result.getDouble("score");
                    if (score >= similarity)
                    {
                        uid = result.getString("user_id");
                        similarity = score;
                        bestFrame = imgStr;
                    }
                }
            }
            second += stepSecond;
            if(picNum > (timeout+5) / stepSecond)
                break;
        }
        grabber.stop();



        File jsonFile = new File("src/main/resources/static/data/table.json");
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> table = objectMapper.readValue(jsonFile, Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>)table.get("data");

        String name = "无";
        String img = "";
        for (Map<String, Object> datum: data)
        {
            if (Objects.equals(datum.get("uid"), uid))
            {
                name = (String)datum.get("oldName");
                img = (String) datum.get("img");
            }
        }

        return "{" +
                "\"lostPerson\": \"" + name + "\"," +
                "\"similarity\": " + similarity + "," +
                "\"bestFrame\": \"" + bestFrame + "\"," +
                "\"img\": \"" + img + "\"" +
                "}";
    }
}
