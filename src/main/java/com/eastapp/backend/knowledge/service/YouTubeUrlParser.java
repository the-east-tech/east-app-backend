package com.eastapp.backend.knowledge.service;

import com.eastapp.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

final class YouTubeUrlParser {
    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private YouTubeUrlParser() {
    }

    static String parseVideoId(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !scheme.equals("http")) {
                throw invalid();
            }

            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            if (host.startsWith("m.")) host = host.substring(2);

            String candidate = null;
            if (host.equals("youtu.be")) {
                candidate = firstPathSegment(uri.getPath());
            } else if (host.equals("youtube.com") || host.equals("youtube-nocookie.com")) {
                String path = uri.getPath() == null ? "" : uri.getPath();
                if (path.equals("/watch")) {
                    candidate = queryParameter(uri.getRawQuery(), "v");
                } else {
                    String[] parts = path.split("/");
                    if (parts.length >= 3 && (
                            parts[1].equals("shorts")
                                    || parts[1].equals("embed")
                                    || parts[1].equals("live")
                    )) {
                        candidate = parts[2];
                    }
                }
            }

            if (candidate == null || !VIDEO_ID.matcher(candidate).matches()) {
                throw invalid();
            }
            return candidate;
        } catch (URISyntaxException exception) {
            throw invalid();
        }
    }

    private static String firstPathSegment(String path) {
        if (path == null) return null;
        for (String part : path.split("/")) {
            if (!part.isBlank()) return part;
        }
        return null;
    }

    private static String queryParameter(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            if (URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals(key)) {
                return parts.length == 2
                        ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                        : "";
            }
        }
        return null;
    }

    private static ApiException invalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_YOUTUBE_URL",
                "Enter a valid YouTube video URL."
        );
    }
}
