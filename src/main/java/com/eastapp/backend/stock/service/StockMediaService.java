package com.eastapp.backend.stock.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.stock.StockMedia;
import com.eastapp.backend.stock.StockMediaRepository;
import com.eastapp.backend.stock.api.StockMediaUploadResponse;
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
public class StockMediaService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final Pattern STORAGE_KEY_PATTERN = Pattern.compile("[0-9a-fA-F-]{36}\\.(jpg|png)");

    private final StockMediaRepository mediaRepository;
    private final TenantRepository tenantRepository;

    public StockMediaService(
            StockMediaRepository mediaRepository,
            TenantRepository tenantRepository
    ) {
        this.mediaRepository = mediaRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public StockMediaUploadResponse saveSkuThumbnail(
            AuthenticatedUser principal,
            MultipartFile file
    ) {
        return saveImage(principal, file, "SKU thumbnail");
    }

    @Transactional
    public StockMediaUploadResponse saveReceivingPhoto(
            AuthenticatedUser principal,
            MultipartFile file
    ) {
        return saveImage(principal, file, "Receiving photo");
    }

    public StoredStockMedia loadSkuThumbnail(
            AuthenticatedUser principal,
            String storageKey
    ) {
        return loadImage(principal, storageKey, "SKU thumbnail");
    }

    public StoredStockMedia loadReceivingPhoto(
            AuthenticatedUser principal,
            String storageKey
    ) {
        return loadImage(principal, storageKey, "Receiving photo");
    }

    private StockMediaUploadResponse saveImage(
            AuthenticatedUser principal,
            MultipartFile file,
            String label
    ) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_REQUIRED", "Take a " + label.toLowerCase(Locale.ROOT) + " first.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE", label + " must not exceed 5 MB.");
        }

        String contentType = normaliseContentType(file.getContentType());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_READ_FAILED", "Unable to read the " + label.toLowerCase(Locale.ROOT) + ".");
        }
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_SIZE", label + " must be between 1 byte and 5 MB.");
        }

        String detectedType = detectImageType(bytes);
        if (!ALLOWED_CONTENT_TYPES.contains(detectedType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_TYPE", label + " must be JPEG or PNG.");
        }
        if (!contentType.isBlank() && !contentType.equals("application/octet-stream")
                && !contentType.equals(detectedType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TYPE_MISMATCH", label + " content does not match its file type.");
        }
        contentType = detectedType;

        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "TENANT_NOT_FOUND", "Login again."));
        String extension = contentType.equals("image/png") ? ".png" : ".jpg";
        String storageKey = UUID.randomUUID() + extension;
        StockMedia saved = mediaRepository.save(new StockMedia(tenant, storageKey, contentType, bytes));
        return new StockMediaUploadResponse(saved.getStorageKey(), saved.getContentType(), saved.getSizeBytes());
    }

    private StoredStockMedia loadImage(
            AuthenticatedUser principal,
            String storageKey,
            String label
    ) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_KEY", "Invalid " + label.toLowerCase(Locale.ROOT) + " key.");
        }

        StockMedia media = mediaRepository.findByTenant_IdAndStorageKey(principal.tenantId(), storageKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", label + " was not found."));
        return new StoredStockMedia(
                new ByteArrayResource(media.getContentBytes()),
                media.getContentType()
        );
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

    public record StoredStockMedia(Resource resource, String contentType) {}
}
