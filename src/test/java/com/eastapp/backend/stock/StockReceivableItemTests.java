package com.eastapp.backend.stock;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StockReceivableItemTests {
    @Test
    void derivesMatchedCondition() {
        assertEquals("Matched", StockReceivableItem.deriveCondition(
                new BigDecimal("12.00"), new BigDecimal("12")
        ));
    }

    @Test
    void derivesShortCondition() {
        assertEquals("Short", StockReceivableItem.deriveCondition(
                new BigDecimal("12"), new BigDecimal("10")
        ));
    }

    @Test
    void derivesExcessCondition() {
        assertEquals("Excess", StockReceivableItem.deriveCondition(
                new BigDecimal("12"), new BigDecimal("14")
        ));
    }
}
