package com.maka.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import com.maka.service.MailService;

@Service
public class MailServiceImpl implements MailService {

    @Autowired
    private JavaMailSender mailSender;

    @Override
    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("1807367347@qq.com");  // 发送人邮箱，改成你的QQ邮箱
        message.setTo(to);                    // 收件人邮箱
        message.setSubject(subject);          // 邮件主题
        message.setText(body);                // 邮件内容
        mailSender.send(message);
    }
}
