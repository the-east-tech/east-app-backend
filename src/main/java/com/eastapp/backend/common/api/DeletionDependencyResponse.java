package com.eastapp.backend.common.api;

public record DeletionDependencyResponse(
        String code,
        String label,
        long count,
        String location
) {}
