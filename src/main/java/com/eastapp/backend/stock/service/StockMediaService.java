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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class StockMediaService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_SKU_THUMBNAIL_DIMENSION = 320;
    private static final int MAX_UNNORMALISED_THUMBNAIL_BYTES = 128 * 1024;
    private static final float SKU_THUMBNAIL_JPEG_QUALITY = 0.70F;
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
        return saveImage(principal, file, "SKU thumbnail", true);
    }

    @Transactional
    public StockMediaUploadResponse saveReceivablePhoto(
            AuthenticatedUser principal,
            MultipartFile file
    ) {
        return saveImage(principal, file, "Receivable photo", false);
    }

    public StoredStockMedia loadSkuThumbnail(
            AuthenticatedUser principal,
            String storageKey
    ) {
        return loadImage(principal, storageKey, "SKU thumbnail", true);
    }

    public StoredStockMedia loadReceivablePhoto(
            AuthenticatedUser principal,
            String storageKey
    ) {
        return loadImage(principal, storageKey, "Receivable photo", false);
    }

    private StockMediaUploadResponse saveImage(
            AuthenticatedUser principal,
            MultipartFile file,
            String label,
            boolean skuThumbnail
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
        if (skuThumbnail) {
            NormalisedImage normalised = normaliseSkuThumbnail(bytes, contentType);
            bytes = normalised.bytes();
            contentType = normalised.contentType();
        }

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
            String label,
            boolean skuThumbnail
    ) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_KEY", "Invalid " + label.toLowerCase(Locale.ROOT) + " key.");
        }

        StockMedia media = mediaRepository.findByTenant_IdAndStorageKey(principal.tenantId(), storageKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", label + " was not found."));
        byte[] bytes = media.getContentBytes();
        String contentType = media.getContentType();
        if (skuThumbnail && bytes.length > MAX_UNNORMALISED_THUMBNAIL_BYTES) {
            NormalisedImage normalised = normaliseSkuThumbnail(bytes, contentType);
            bytes = normalised.bytes();
            contentType = normalised.contentType();
        }
        return new StoredStockMedia(
                new ByteArrayResource(bytes),
                contentType
        );
    }

    private NormalisedImage normaliseSkuThumbnail(byte[] bytes, String contentType) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            BufferedImage source = ImageIO.read(input);
            if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
                return new NormalisedImage(bytes, contentType);
            }

            double scale = Math.min(
                    1.0D,
                    (double) MAX_SKU_THUMBNAIL_DIMENSION / Math.max(source.getWidth(), source.getHeight())
            );
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = thumbnail.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) return new NormalisedImage(bytes, contentType);
            ImageWriter writer = writers.next();
            try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                 ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(imageOutput);
                ImageWriteParam parameters = writer.getDefaultWriteParam();
                if (parameters.canWriteCompressed()) {
                    parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    parameters.setCompressionQuality(SKU_THUMBNAIL_JPEG_QUALITY);
                }
                writer.write(null, new IIOImage(thumbnail, null, null), parameters);
                return new NormalisedImage(output.toByteArray(), "image/jpeg");
            } finally {
                writer.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            return new NormalisedImage(bytes, contentType);
        }
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

    private record NormalisedImage(byte[] bytes, String contentType) {}
}
