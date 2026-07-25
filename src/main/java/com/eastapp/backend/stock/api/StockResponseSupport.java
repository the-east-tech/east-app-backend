package com.eastapp.backend.stock.api;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class StockResponseSupport {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final DateTimeFormatter LABEL =
            DateTimeFormatter.ofPattern("d MMM uuuu, h:mm a", Locale.ENGLISH).withZone(ZONE);

    private StockResponseSupport() {}

    static String label(Instant value) {
        return value == null ? "" : LABEL.format(value);
    }
}
