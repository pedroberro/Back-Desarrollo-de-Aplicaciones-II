package com.daii.backdaii.service;

public interface EmailService {

    void sendPasswordResetEmail(String to, String resetLink);
}