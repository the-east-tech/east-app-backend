package com.eastapp.backend.stock.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateStockBalanceRequest(
        @NotNull @DecimalMin("0") BigDecimal balance
) {}
