package com.bakaru.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    public void sendOrderConfirmation(Long orderId, Long customerId) {
        log.info("📧 Sending order confirmation - orderId: {}, customerId: {}",
                orderId, customerId);
        // TODO: conectar con Kafka para recibir eventos
        // TODO: integrar con servicio de email real (SendGrid, SES, etc.)
    }

    public void sendOrderCancellation(Long orderId, Long customerId) {
        log.info("📧 Sending order cancellation - orderId: {}, customerId: {}",
                orderId, customerId);
    }

    public void sendPaymentConfirmation(Long orderId, Long customerId) {
        log.info("💳 Sending payment confirmation - orderId: {}, customerId: {}",
                orderId, customerId);
    }

    public void sendPaymentFailed(Long orderId, Long customerId) {
        log.info("❌ Sending payment failed notification - orderId: {}, customerId: {}",
                orderId, customerId);
    }
}