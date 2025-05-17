package com.maka.controller;

import com.maka.pojo.SmsRequest; // 引入 SmsRequest 类
import com.maka.service.SmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sms")
public class SmsController {

    @Autowired
    private SmsService smsService;

    @PostMapping("/send")
    public String sendSms(@RequestBody SmsRequest request) {
        smsService.sendSms(request.getPhoneNumber(), request.getLocation(),
                request.getElderlyName(), request.getConfirmationLevel(), request.getContactNumber());
        return "短信已发送";
    }

}
