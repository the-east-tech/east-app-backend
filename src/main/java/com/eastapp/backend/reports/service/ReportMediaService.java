package com.eastapp.backend.reports.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.reports.ReportMedia;
import com.eastapp.backend.reports.ReportMediaRepository;
import com.eastapp.backend.reports.api.ReportMediaUploadResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class ReportMediaService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final Pattern STORAGE_KEY_PATTERN = Pattern.compile("[0-9a-fA-F-]{36}\\.(jpg|png)");

    private final ReportMediaRepository mediaRepository;

    public ReportMediaService(ReportMediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @Transactional
    public ReportMediaUploadResponse saveImage(AuthenticatedUser principal, MultipartFile file) {
        ReportMedia saved = saveImageEntity(principal, file);
        return new ReportMediaUploadResponse(
                saved.getStorageKey(),
                saved.getContentType(),
                saved.getSizeBytes()
        );
    }

    @Transactional
    public ReportMedia saveImageEntity(AuthenticatedUser principal, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_IMAGE_REQUIRED", "Take a report photo first.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_IMAGE_TOO_LARGE", "Report photo must not exceed 5 MB.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_IMAGE_READ_FAILED", "Unable to read the report photo.");
        }
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_IMAGE_SIZE", "Report photo must be between 1 byte and 5 MB.");
        }

        String detectedType = detectImageType(bytes);
        if (!ALLOWED_CONTENT_TYPES.contains(detectedType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_IMAGE_TYPE", "Report photo must be JPEG or PNG.");
        }
        String suppliedType = normaliseContentType(file.getContentType());
        if (!suppliedType.isBlank()
                && !suppliedType.equals("application/octet-stream")
                && !suppliedType.equals(detectedType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_IMAGE_TYPE_MISMATCH", "Report photo content does not match its file type.");
        }

        String extension = detectedType.equals("image/png") ? ".png" : ".jpg";
        String storageKey = UUID.randomUUID() + extension;
        return mediaRepository.saveAndFlush(new ReportMedia(
                principal.tenantId(),
                storageKey,
                detectedType,
                bytes,
                principal.userId()
        ));
    }

    public StoredReportMedia loadImage(AuthenticatedUser principal, String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_MEDIA_KEY", "Invalid report photo key.");
        }
        ReportMedia media = mediaRepository.findByTenantIdAndStorageKey(principal.tenantId(), storageKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPORT_MEDIA_NOT_FOUND", "Report photo was not found."));
        return new StoredReportMedia(new ByteArrayResource(media.getContentBytes()), media.getContentType());
    }

    public ReportMedia requireOwnedMedia(AuthenticatedUser principal, String storageKey) {
        ReportMedia media = mediaRepository.findByTenantIdAndStorageKey(principal.tenantId(), storageKey)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "REPORT_MEDIA_NOT_FOUND", "Upload the report photo again."));
        if (!media.getUploadedByUserId().equals(principal.userId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "REPORT_MEDIA_NOT_OWNED",
                    "Take and upload a new report photo from ur own account."
            );
        }
        return media;
    }

    public ReportMedia requireTenantMedia(AuthenticatedUser principal, String storageKey) {
        return mediaRepository.findByTenantIdAndStorageKey(principal.tenantId(), storageKey)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "REPORT_MEDIA_NOT_FOUND",
                        "Upload the image again."
                ));
    }

    private static String normaliseContentType(String contentType) {
        if (contentType == null) return "";
        String value = contentType.toLowerCase(Locale.ROOT).trim();
        return value.equals("image/jpg") ? "image/jpeg" : value;
    }

    private static String detectImageType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return "image/png";
        }
        return "";
    }

    public record StoredReportMedia(Resource resource, String contentType) {
    }
}
