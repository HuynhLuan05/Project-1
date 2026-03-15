package com.yas.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryServiceTest {

    private final DeliveryService deliveryService = new DeliveryService();

    @Test
    void getStatus_returnsReadyMessage() {
        String status = deliveryService.getStatus();
        assertThat(status).isEqualTo("DELIVERY_SERVICE_READY");
    }
}
