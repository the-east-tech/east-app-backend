package com.eastapp.backend.stock.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkReviewStockCountsRequest(
        @NotEmpty
        @Size(max = 100)
        List<@NotNull UUID> submissionIds,

        @NotBlank
        @Pattern(regexp = "Approved|Rejected|DONE|PENDING")
        String status,

        @Size(max = 1000)
        String note
) {
}
