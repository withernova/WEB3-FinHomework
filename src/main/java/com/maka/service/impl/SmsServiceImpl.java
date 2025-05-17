package com.maka.service.impl;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.maka.service.SmsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsServiceImpl implements SmsService {

    @Value("${aliyun.sms.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.sms.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.sms.sign-name}")
    private String signName;

    @Value("${aliyun.sms.template-code}")
    private String templateCode;

    @Override
    public void sendSms(String phoneNumber, String location,
                        String elderlyName, String confirmationLevel,
                        String contactNumber) {
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint("dysmsapi.aliyuncs.com");

            Client client = new Client(config);

            // 创建发送短信请求
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phoneNumber)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam("{\"location\":\"" + location + "\","
                            + "\"elderlyName\":\"" + elderlyName + "\","
                            + "\"confirmationLevel\":\"" + confirmationLevel + "\","
                            + "\"contactNumber\":\"" + contactNumber + "\"}");

            SendSmsResponse response = client.sendSms(request);

            // 检查响应
            System.out.println("Response Code: " + response.getBody().code);
            System.out.println("Response Message: " + response.getBody().message);

            if ("OK".equals(response.getBody().code)) {
                System.out.println("短信发送成功！");
            } else {
                System.out.println("短信发送失败，错误码：" + response.getBody().code);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("短信发送时出现异常：" + e.getMessage());
        }
    }
}
