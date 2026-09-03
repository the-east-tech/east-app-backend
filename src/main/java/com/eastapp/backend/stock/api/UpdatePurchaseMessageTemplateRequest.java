package com.eastapp.backend.stock.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePurchaseMessageTemplateRequest(
        @NotBlank @Size(max = 2000) String messageTemplate
) {}
