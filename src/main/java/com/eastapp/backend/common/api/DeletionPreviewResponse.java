package com.eastapp.backend.common.api;

import java.util.List;

public record DeletionPreviewResponse(
        boolean deletable,
        List<DeletionDependencyResponse> dependencies
) {}
