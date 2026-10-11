package com.example.moodecho.service;

public interface MailService {

    void sendTextMail(String to, String subject, String content);
}