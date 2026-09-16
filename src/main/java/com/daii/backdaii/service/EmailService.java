package com.daii.backdaii.service;

public interface EmailService {

    void sendPasswordResetEmail(String to, String resetLink);

    void sendEmailConfirmation(String to, String nombre, String confirmLink);
}