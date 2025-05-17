package com.maka.service;

public interface SmsService {
    void sendSms(String phoneNumber, String location,
                  String elderlyName, String confirmationLevel,
                  String contactNumber);
}
