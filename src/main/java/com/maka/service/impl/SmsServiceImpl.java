package com.maka.service.impl;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.maka.service.SmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

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
    
    @Value("${aliyun.sms.verification-template-code:SMS_1234567890}")
    private String verificationTemplateCode;
    
    @Autowired
    private StringRedisTemplate redisTemplate;

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
    
    @Override
    public boolean sendVerificationCode(String phoneNumber) {
        try {
            // 生成6位随机验证码
            String code = generateRandomCode();
            
            // 将验证码存入Redis，设置5分钟过期
            String key = "sms:verification:" + phoneNumber;
            redisTemplate.opsForValue().set(key, code, 5, TimeUnit.MINUTES);
            
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint("dysmsapi.aliyuncs.com");

            Client client = new Client(config);

            // 创建发送验证码短信请求
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phoneNumber)
                    .setSignName(signName)
                    .setTemplateCode(verificationTemplateCode)
                    .setTemplateParam("{\"code\":\"" + code + "\"}");

            SendSmsResponse response = client.sendSms(request);

            // 检查响应
            System.out.println("Verification Code Response: " + response.getBody().code);
            
            return "OK".equals(response.getBody().code);
            
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("验证码短信发送时出现异常：" + e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean verifyCode(String phoneNumber, String code) {
        String key = "sms:verification:" + phoneNumber;
        String storedCode = redisTemplate.opsForValue().get(key);
        
        if (storedCode != null && storedCode.equals(code)) {
            // 验证成功后删除验证码
            redisTemplate.delete(key);
            return true;
        }
        
        return false;
    }
    
    private String generateRandomCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}