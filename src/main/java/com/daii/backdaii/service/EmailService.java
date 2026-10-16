package com.daii.backdaii.service;

public interface EmailService {

    void sendPasswordResetEmail(String to, String resetLink);

    void sendEmailConfirmation(String to, String nombre, String confirmLink);

    /** Tells a customer that an administrator changed or removed one of their bookings. */
    void sendAvisoReserva(String to, String nombre, String titulo, String detalle);

    /** Operational alert for the team (e.g. a message in the dead-letter queue). */
    void sendAlerta(String to, String titulo, String detalle);
}