package com.eastapp.backend.common.logging;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Produces one useful log line for each API request. The line identifies the
 * authenticated caller and business context and includes compact query/body
 * data for development tracing. Credentials and binary uploads are masked.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final int REQUEST_CACHE_LIMIT_BYTES = 64 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = shortRequestId();
        long startedAt = System.nanoTime();
        boolean failedBeforeResponse = false;
        ContentCachingRequestWrapper cachedRequest = request instanceof ContentCachingRequestWrapper existing
                ? existing
                : new ContentCachingRequestWrapper(request, REQUEST_CACHE_LIMIT_BYTES);

        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);

        try {
            filterChain.doFilter(cachedRequest, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failedBeforeResponse = true;
            throw exception;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            int status = failedBeforeResponse && response.getStatus() < 400
                    ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    : response.getStatus();

            byte[] bodyBytes = cachedRequest.getContentAsByteArray();
            AuthenticatedUser user = authenticatedUser();
            String caller = user == null
                    ? RequestLogSanitizer.claimedCaller(bodyBytes, cachedRequest)
                    : "authenticated";

            log.info(
                    "HTTP requestId={} method={} path={} query={} status={} durationMs={} "
                            + "caller={} employeeId={} fullName={} businessCode={} "
                            + "businessName={} contentType={} body={}",
                    requestId,
                    cachedRequest.getMethod(),
                    cachedRequest.getRequestURI(),
                    RequestLogSanitizer.query(cachedRequest.getQueryString()),
                    status,
                    durationMs,
                    caller,
                    user == null ? "-" : user.employeeId(),
                    user == null ? "-" : RequestLogSanitizer.safeValue(user.fullName()),
                    user == null ? "-" : RequestLogSanitizer.safeValue(user.tenantCode()),
                    user == null ? "-" : RequestLogSanitizer.safeValue(user.tenantName()),
                    cachedRequest.getContentType() == null ? "-" : cachedRequest.getContentType(),
                    RequestLogSanitizer.body(bodyBytes, cachedRequest)
            );

            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private static AuthenticatedUser authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user;
    }

    private static String shortRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
