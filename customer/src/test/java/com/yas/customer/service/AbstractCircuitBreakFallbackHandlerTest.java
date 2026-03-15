package com.yas.customer.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class AbstractCircuitBreakFallbackHandlerTest {

    private static class ConcreteHandler extends AbstractCircuitBreakFallbackHandler {
        void callHandleBodilessFallback(Throwable t) throws Throwable {
            handleBodilessFallback(t);
        }

        <T> T callHandleTypedFallback(Throwable t) throws Throwable {
            return handleTypedFallback(t);
        }
    }

    @Test
    void handleBodilessFallback_rethrowsThrowable() {
        ConcreteHandler handler = new ConcreteHandler();
        Throwable t = new RuntimeException("test");
        Throwable thrown = assertThrows(Throwable.class, () -> handler.callHandleBodilessFallback(t));
        assertSame(t, thrown);
    }

    @Test
    void handleTypedFallback_rethrowsThrowable() {
        ConcreteHandler handler = new ConcreteHandler();
        Throwable t = new RuntimeException("test");
        Throwable thrown = assertThrows(Throwable.class, () -> handler.callHandleTypedFallback(t));
        assertSame(t, thrown);
    }
}
