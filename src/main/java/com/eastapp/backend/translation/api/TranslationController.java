package com.eastapp.backend.translation.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.translation.service.TranslationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/translations")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @GetMapping("/status")
    TranslationStatusResponse status() {
        return translationService.status();
    }

    @PostMapping
    TranslateTextResponse translate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody TranslateTextRequest request
    ) {
        return translationService.translate(principal, request);
    }

    @PostMapping("/preview")
    TranslationPreviewResponse preview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody TranslateTextRequest request
    ) {
        return translationService.preview(principal, request);
    }
}
