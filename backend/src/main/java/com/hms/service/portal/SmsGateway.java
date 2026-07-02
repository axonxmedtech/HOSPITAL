package com.hms.service.portal;

/** Delivers a one-time OTP code to a mobile number. One method, one responsibility. */
public interface SmsGateway {
    void send(String mobile, String otp);
}
