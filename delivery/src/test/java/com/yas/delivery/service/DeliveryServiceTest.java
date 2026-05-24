package com.yas.delivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.yas.delivery.DeliveryApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = DeliveryApplication.class)
class DeliveryServiceTest {

    @Autowired
    private DeliveryService deliveryService;

    @Test
    void getStatus_whenCalled_returnsReadyMessage() {
        String status = deliveryService.getStatus();
        assertNotNull(status);
        assertEquals("DELIVERY_SERVICE_READY", status);
    }
}
