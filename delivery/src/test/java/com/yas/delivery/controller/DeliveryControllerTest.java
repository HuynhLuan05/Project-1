package com.yas.delivery.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yas.delivery.service.DeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DeliveryController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeliveryControllerTest {

    @MockitoBean
    private DeliveryService deliveryService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetStatus_whenCalled_thenReturnOkWithStatus() throws Exception {
        given(deliveryService.getStatus()).willReturn("DELIVERY_SERVICE_READY");

        this.mockMvc.perform(get("/delivery/status"))
            .andExpect(status().isOk())
            .andExpect(content().string("DELIVERY_SERVICE_READY"));
    }
}
