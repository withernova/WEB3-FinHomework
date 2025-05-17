package com.maka.controller;

import com.maka.pojo.NotificationRequest;
import com.maka.service.MailService;
import com.maka.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.json.JSONObject;
import org.json.JSONArray;

import java.time.format.DateTimeFormatter;

@RestController
public class MailController {

    @Autowired
    private MailService mailService;

    @Autowired
    private AddressService addressService;

    private final String API_KEY = "d688586468dd49b708f89590e83dd259";
    private final String GEOCODING_URL = "https://restapi.amap.com/v3/geocode/geo";

    @PostMapping("/sendNotification")
    public String sendNotification(@RequestBody NotificationRequest request) {
        System.out.println("接收到的请求: " + request);
        String lostLocation = request.getLostLocation();
        // 转换性别
        String genderDisplay = "male".equals(request.getGender()) ? "男" : "女";
        // 格式化时间
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String lostTimeFormatted = request.getLostTime().format(formatter);
        // 转换地址为经纬度
        String location = getCoordinates(lostLocation);
        if (location == null) {
            return "地址无法转换为经纬度";
        }
        String[] latLng = location.split(",");
        double lostLongitude = Double.parseDouble(latLng[0]);
        double lostLatitude = Double.parseDouble(latLng[1]);

        String closestEmail = addressService.findClosestAddressEmail(lostLongitude, lostLatitude);

        if (closestEmail == null) {
            return "未找到合适的地址";
        }

        String subject = "紧急通知: 寻找失踪人员";
        String body = String.format(
                "紧急通知！\n\n" +
                        "尊敬的相关人员，\n\n" +
                        "我们正在寻找一位失踪老人，请您密切注意。\n\n" +
                        "失踪老人信息如下：\n" +
                        "姓名: %s\n" +
                        "性别: %s\n" +
                        "年龄: %s\n" +
                        "走失时间: %s\n" +
                        "走失地点: %s\n" +
                        "家属联系方式: %s\n\n" +
                        "请您如果有相关信息，请及时与家属联系。感谢您的配合！\n\n" +
                        "此致，\n" +
                        "紧急通知小组",
                request.getName(), genderDisplay, request.getAge(),
                lostTimeFormatted, lostLocation, request.getContactInfo()
        );

        mailService.sendEmail(closestEmail, subject, body);
        return "通知已发送成功！";
    }


    // 该方法用于根据给定地址获取地理坐标
    private String getCoordinates(String address) {
        RestTemplate restTemplate = new RestTemplate();
        String url = String.format("%s?address=%s&key=%s", GEOCODING_URL, address, API_KEY);

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        String jsonResponse = response.getBody();

        System.out.println("API 返回的响应: " + jsonResponse);

        JSONObject jsonObject = new JSONObject(jsonResponse);
        if (jsonObject.getString("status").equals("0")) {
            System.out.println("错误信息: " + jsonObject.getString("info")); // 输出错误信息
            return null; // 处理错误
        }

        JSONArray geocodes = jsonObject.getJSONArray("geocodes");

        if (geocodes.length() > 0) {
            JSONObject location = geocodes.getJSONObject(0);
            return location.getString("location");
        } else {
            return null;
        }
    }

}
