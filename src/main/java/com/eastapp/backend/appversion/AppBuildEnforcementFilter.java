package com.eastapp.backend.appversion;

import com.eastapp.backend.appversion.config.AppVersionProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class AppBuildEnforcementFilter extends OncePerRequestFilter {

    public static final String APP_VERSION_HEADER = "X-Flow-App-Version";
    public static final String APP_BUILD_HEADER = "X-Flow-App-Build";
    public static final String APP_PLATFORM_HEADER = "X-Flow-App-Platform";

    private static final String POLICY_PATH = "/api/v1/app-version/policy";
    private static final String UPDATE_REQUIRED_RESPONSE = """
            {"code":"APP_UPDATE_REQUIRED","message":"Update Flow to the latest version to continue."}
            """;

    private final AppVersionProperties properties;

    public AppBuildEnforcementFilter(AppVersionProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || POLICY_PATH.equals(path)
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isLatestSupportedBuild(request)) {
            writeUpdateRequired(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isLatestSupportedBuild(HttpServletRequest request) {
        String version = request.getHeader(APP_VERSION_HEADER);
        String rawBuildNumber = request.getHeader(APP_BUILD_HEADER);
        String rawPlatform = request.getHeader(APP_PLATFORM_HEADER);
        if (version == null || version.isBlank()
                || rawBuildNumber == null || rawBuildNumber.isBlank()
                || rawPlatform == null || rawPlatform.isBlank()) {
            return false;
        }

        try {
            AppPlatform platform = AppPlatform.valueOf(
                    rawPlatform.trim().toUpperCase(Locale.ROOT)
            );
            long buildNumber = Long.parseLong(rawBuildNumber.trim());
            var release = properties.forPlatform(platform);
            return version.trim().equals(release.getLatestVersion())
                    && buildNumber >= release.getLatestBuildNumber();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void writeUpdateRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UPGRADE_REQUIRED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write(UPDATE_REQUIRED_RESPONSE);
    }
}
