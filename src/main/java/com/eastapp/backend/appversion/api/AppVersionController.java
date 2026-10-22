package com.eastapp.backend.appversion.api;

import com.eastapp.backend.appversion.AppPlatform;
import com.eastapp.backend.appversion.config.AppVersionProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/app-version")
public class AppVersionController {

    private final AppVersionProperties properties;

    public AppVersionController(AppVersionProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/policy")
    ResponseEntity<AppVersionPolicyResponse> policy(
            @RequestParam AppPlatform platform,
            @RequestParam @NotBlank String version,
            @RequestParam @Min(1) long buildNumber
    ) {
        var release = properties.forPlatform(platform);
        var response = new AppVersionPolicyResponse(
                platform,
                version.trim(),
                buildNumber,
                release.getLatestVersion(),
                release.getLatestBuildNumber(),
                buildNumber < release.getLatestBuildNumber(),
                release.getUpdateUrl()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
