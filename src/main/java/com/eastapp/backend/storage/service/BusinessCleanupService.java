package com.eastapp.backend.storage.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.storage.api.BusinessCleanupCompleteResponse;
import com.eastapp.backend.storage.api.BusinessCleanupDataType;
import com.eastapp.backend.storage.api.BusinessCleanupMediaMode;
import com.eastapp.backend.storage.api.BusinessCleanupMediaType;
import com.eastapp.backend.storage.api.BusinessCleanupPreviewResponse;
import com.eastapp.backend.storage.api.BusinessCleanupRequest;
import com.eastapp.backend.storage.api.BusinessCleanupRunResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BusinessCleanupService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final long MAX_BACKUP_RECORDS = 100_000;
    private static final long MAX_ESTIMATED_ZIP_BYTES = 300L * 1024 * 1024;
    private static final int RECENT_RUN_LIMIT = 20;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final String SKU_ELIGIBLE = """
            sku.tenant_id = :tenantId
            and not sku.active
            and sku.updated_at::date < :cutoffDate
            and sku.updated_at <= :asOf
            and not exists (select 1 from stock_count_submissions count_record where count_record.sku_id = sku.id)
            and not exists (select 1 from stock_receivable_items item where item.sku_id = sku.id)
            and not exists (select 1 from waste_report_details waste where waste.sku_id = sku.id)
            and not exists (select 1 from stock_sku_change_requests request where request.sku_id = sku.id)
            """;
    private static final String SUPPLIER_ELIGIBLE = """
            supplier.tenant_id = :tenantId
            and not supplier.active
            and supplier.updated_at::date < :cutoffDate
            and supplier.updated_at <= :asOf
            and not exists (select 1 from stock_receivables receivable where receivable.supplier_id = supplier.id)
            and not exists (select 1 from stock_sku_suppliers link where link.supplier_id = supplier.id)
            """;
    private static final String TAG_ELIGIBLE = """
            tag.tenant_id = :tenantId
            and not tag.active
            and tag.updated_at::date < :cutoffDate
            and tag.updated_at <= :asOf
            and not exists (select 1 from stock_skus sku where sku.tag1_id = tag.id or sku.tag2_id = tag.id)
            and not exists (select 1 from knowledge_sops sop where sop.tag_id = tag.id)
            and not exists (select 1 from task_templates template where template.tag_id = tag.id)
            and not exists (select 1 from task_records record where record.tag_id = tag.id)
            """;
    private static final String TASK_TEMPLATE_ELIGIBLE = """
            template.tenant_id = :tenantId
            and not template.active
            and template.updated_at::date < :cutoffDate
            and template.updated_at <= :asOf
            and not exists (select 1 from task_records task where task.template_id = template.id)
            """;
    private static final String ADVERTISEMENT_ELIGIBLE = """
            advertisement.tenant_id = :tenantId
            and (
                (not advertisement.active and advertisement.updated_at::date < :cutoffDate)
                or advertisement.ends_at::date < :cutoffDate
            )
            and advertisement.updated_at <= :asOf
            and advertisement.ends_at <= :asOf
            """;
    private static final String COUNT_ELIGIBLE = """
            count_record.tenant_id = :tenantId
            and count_record.review_status = 'DONE'
            and count_record.captured_at::date < :cutoffDate
            and count_record.updated_at <= :asOf
            """;
    private static final String RECEIVABLE_ELIGIBLE = """
            receivable.tenant_id = :tenantId
            and receivable.review_status = 'DONE'
            and receivable.captured_at::date < :cutoffDate
            and receivable.updated_at <= :asOf
            """;
    private static final String SKU_REQUEST_ELIGIBLE = """
            request.tenant_id = :tenantId
            and request.workflow_status = 'DONE'
            and request.updated_at::date < :cutoffDate
            and request.updated_at <= :asOf
            """;
    private static final String REPORT_ELIGIBLE = """
            report.tenant_id = :tenantId
            and report.workflow_status = 'DONE'
            and report.report_date < :cutoffDate
            and report.updated_at <= :asOf
            """;
    private static final String TASK_ELIGIBLE = """
            task.tenant_id = :tenantId
            and task.status = 'DONE'
            and task.task_date < :cutoffDate
            and task.updated_at <= :asOf
            """;
    private static final String ATTENDANCE_ELIGIBLE = """
            attendance.tenant_id = :tenantId
            and attendance.occurred_at::date < :cutoffDate
            and attendance.created_at <= :asOf
            """;
    private static final String ACTIVITY_ELIGIBLE = """
            event.tenant_id = :tenantId
            and event.occurred_at::date < :cutoffDate
            and event.occurred_at <= :asOf
            and not exists (
                select 1 from user_notifications notification
                where notification.activity_event_id = event.id
                  and notification.read_at is null
                  and notification.dismissed_at is null
            )
            """;
    private static final String VIDEO_ELIGIBLE = """
            watch.tenant_id = :tenantId
            and watch.started_at::date < :cutoffDate
            and watch.updated_at <= :asOf
            """;
    private static final String UNUSED_STOCK_MEDIA = """
            not exists (select 1 from stock_skus sku where sku.tenant_id = candidate.tenant_id and sku.thumbnail_media_id = candidate.id)
            and not exists (
                select 1 from stock_count_submissions count_record
                where count_record.tenant_id = candidate.tenant_id
                  and (count_record.stock_photo_name = candidate.storage_key or count_record.invoice_photo_name = candidate.storage_key)
            )
            and not exists (
                select 1 from stock_receivables receivable
                where receivable.tenant_id = candidate.tenant_id
                  and (receivable.invoice_photo_name = candidate.storage_key or receivable.goods_photo_name = candidate.storage_key)
            )
            and not exists (
                select 1 from stock_sku_change_requests request
                where request.tenant_id = candidate.tenant_id
                  and request.workflow_status <> 'DONE'
                  and request.payload_json::jsonb ->> 'photoPath' = candidate.storage_key
            )
            """;
    private static final String UNUSED_REPORT_MEDIA = """
            not exists (select 1 from advertisements advertisement where advertisement.tenant_id = candidate.tenant_id and advertisement.image_storage_key = candidate.storage_key)
            and not exists (select 1 from sales_void_bills bill where bill.tenant_id = candidate.tenant_id and bill.photo_media_id = candidate.id)
            and not exists (select 1 from waste_report_details waste where waste.tenant_id = candidate.tenant_id and waste.photo_media_id = candidate.id)
            and not exists (select 1 from daily_report_photos photo where photo.tenant_id = candidate.tenant_id and photo.photo_media_id = candidate.id)
            and not exists (select 1 from complaint_report_details complaint where complaint.tenant_id = candidate.tenant_id and complaint.photo_media_id = candidate.id)
            and not exists (select 1 from task_photos task_photo where task_photo.tenant_id = candidate.tenant_id and task_photo.photo_media_id = candidate.id)
            """;

    private static final Map<BusinessCleanupDataType, String> DESCRIPTIONS = Map.of(
            BusinessCleanupDataType.INACTIVE_SETUP, "Inactive SKUs, suppliers, tags and task templates without protected references, plus old advertisements.",
            BusinessCleanupDataType.STOCK_HISTORY, "Completed stock counts, receivables and SKU requests before the cutoff date.",
            BusinessCleanupDataType.REPORTS, "Completed business reports before the cutoff date and their details.",
            BusinessCleanupDataType.TASKS, "Completed task records before the cutoff date and their checklist results.",
            BusinessCleanupDataType.ATTENDANCE, "Attendance events before the cutoff date.",
            BusinessCleanupDataType.ACTIVITY, "Activity events before the cutoff date with no unread notification.",
            BusinessCleanupDataType.VIDEO_ANALYTICS, "SOP video viewing sessions before the cutoff date."
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public BusinessCleanupService(
            NamedParameterJdbcTemplate jdbcTemplate,
            JsonMapper jsonMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public BusinessCleanupPreviewResponse preview(
            AuthenticatedUser principal,
            BusinessCleanupRequest request
    ) {
        validate(request);
        return snapshot(principal, request, Instant.now()).response();
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public BackupFile createBackup(
            AuthenticatedUser principal,
            BusinessCleanupRequest request
    ) {
        validate(request);
        Instant createdAt = Instant.now();
        Snapshot snapshot = snapshot(principal, request, createdAt);
        if (snapshot.recordRefs().isEmpty()) {
            throw badRequest("BUSINESS_CLEANUP_EMPTY", "No eligible business records match this selection.");
        }
        if (snapshot.recordRefs().size() > MAX_BACKUP_RECORDS) {
            throw badRequest(
                    "BUSINESS_CLEANUP_TOO_MANY_RECORDS",
                    "Choose an earlier cutoff in smaller stages; one backup is limited to " + MAX_BACKUP_RECORDS + " business records."
            );
        }
        if (snapshot.response().estimatedZipBytes() > MAX_ESTIMATED_ZIP_BYTES) {
            throw badRequest(
                    "BUSINESS_CLEANUP_BACKUP_TOO_LARGE",
                    "This backup is estimated above 300 MB. Choose fewer data or photo types and create more than one backup."
            );
        }

        UUID runId = UUID.randomUUID();
        String fileName = fileName(principal, createdAt);
        byte[] bytes = createZip(runId, principal, request, snapshot, createdAt);
        String sha256 = sha256(bytes);
        MapSqlParameterSource parameters = baseParameters(principal, request, createdAt)
                .addValue("id", runId)
                .addValue("requestedBy", principal.userId())
                .addValue("dataTypes", enumText(request.dataTypes()))
                .addValue("mediaMode", request.mediaMode().name())
                .addValue("mediaTypes", enumText(selectedMediaTypes(request)))
                .addValue("recordCount", snapshot.response().recordCount())
                .addValue("blockedRecordCount", snapshot.response().blockedRecordCount())
                .addValue("photoCount", snapshot.response().photoCount())
                .addValue("excludedPhotoCount", snapshot.response().excludedPhotoCount())
                .addValue("estimatedZipBytes", snapshot.response().estimatedZipBytes())
                .addValue("selectionSha256", snapshot.selectionSha256())
                .addValue("zipFileName", fileName)
                .addValue("zipSizeBytes", bytes.length)
                .addValue("zipSha256", sha256);
        jdbcTemplate.update(
                """
                insert into business_cleanup_runs (
                    id, tenant_id, requested_by_user_id, cutoff_date, data_types,
                    media_mode, media_types, record_count, blocked_record_count,
                    photo_count, excluded_photo_count, estimated_zip_bytes,
                    selection_sha256, zip_file_name, zip_size_bytes, zip_sha256,
                    status, created_at
                ) values (
                    :id, :tenantId, :requestedBy, :cutoffDate, :dataTypes,
                    :mediaMode, :mediaTypes, :recordCount, :blockedRecordCount,
                    :photoCount, :excludedPhotoCount, :estimatedZipBytes,
                    :selectionSha256, :zipFileName, :zipSizeBytes, :zipSha256,
                    'BACKUP_CREATED', :asOf
                )
                """,
                parameters
        );
        return new BackupFile(runId, fileName, sha256, bytes);
    }

    @Transactional(readOnly = true)
    public List<BusinessCleanupRunResponse> runs(AuthenticatedUser principal) {
        return jdbcTemplate.query(
                """
                select * from business_cleanup_runs
                where tenant_id = :tenantId
                order by created_at desc
                limit :limit
                """,
                new MapSqlParameterSource()
                        .addValue("tenantId", principal.tenantId())
                        .addValue("limit", RECENT_RUN_LIMIT),
                (resultSet, rowNumber) -> runResponse(new RunRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("tenant_id", UUID.class),
                        resultSet.getObject("requested_by_user_id", UUID.class),
                        resultSet.getObject("cutoff_date", LocalDate.class),
                        resultSet.getString("data_types"),
                        resultSet.getString("media_mode"),
                        resultSet.getString("media_types"),
                        resultSet.getLong("record_count"),
                        resultSet.getLong("blocked_record_count"),
                        resultSet.getLong("photo_count"),
                        resultSet.getLong("excluded_photo_count"),
                        resultSet.getLong("estimated_zip_bytes"),
                        resultSet.getString("selection_sha256"),
                        resultSet.getString("zip_file_name"),
                        resultSet.getLong("zip_size_bytes"),
                        resultSet.getString("zip_sha256"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        instant(resultSet.getTimestamp("saved_confirmed_at")),
                        instant(resultSet.getTimestamp("completed_at")),
                        resultSet.getLong("deleted_record_count"),
                        resultSet.getLong("deleted_photo_count")
                ))
        );
    }

    @Transactional
    public BusinessCleanupRunResponse confirmSaved(AuthenticatedUser principal, UUID runId) {
        RunRow run = run(principal, runId);
        if (run.status().equals("COMPLETED")) return runResponse(run);
        if (!run.status().equals("BACKUP_CREATED") && !run.status().equals("SAVED_CONFIRMED")) {
            throw conflict("BUSINESS_CLEANUP_RUN_STATE_INVALID", "This backup cannot be confirmed in its current state.");
        }
        if (run.status().equals("BACKUP_CREATED")) {
            jdbcTemplate.update(
                    """
                    update business_cleanup_runs
                    set status = 'SAVED_CONFIRMED', saved_confirmed_at = current_timestamp
                    where id = :id and tenant_id = :tenantId
                    """,
                    runParameters(principal, runId)
            );
        }
        return runResponse(run(principal, runId));
    }

    @Transactional
    public BusinessCleanupCompleteResponse cleanup(AuthenticatedUser principal, UUID runId) {
        jdbcTemplate.query(
                "select pg_advisory_xact_lock(hashtext(cast(:tenantId as text)))",
                new MapSqlParameterSource("tenantId", principal.tenantId()),
                resultSet -> null
        );
        RunRow run = run(principal, runId);
        if (!run.status().equals("SAVED_CONFIRMED")) {
            throw conflict(
                    "BUSINESS_CLEANUP_BACKUP_NOT_CONFIRMED",
                    "Confirm that the ZIP was saved successfully before permanent cleanup."
            );
        }

        BusinessCleanupRequest request = request(run);
        Snapshot current = snapshot(principal, request, run.createdAt());
        if (!MessageDigest.isEqual(
                run.selectionSha256().getBytes(StandardCharsets.UTF_8),
                current.selectionSha256().getBytes(StandardCharsets.UTF_8)
        )) {
            throw conflict(
                    "BUSINESS_CLEANUP_SELECTION_CHANGED",
                    "Eligible data or photo links changed after backup. Create and save a fresh backup before deleting."
            );
        }

        Map<String, List<UUID>> ids = current.recordRefs().stream()
                .collect(Collectors.groupingBy(
                        RecordRef::tableName,
                        LinkedHashMap::new,
                        Collectors.mapping(RecordRef::id, Collectors.toList())
                ));
        deleteNotifications(current.notificationIds(), principal.tenantId());
        long deletedRecords = deleteEvents(
                ids.getOrDefault("activity_events", List.of()),
                principal.tenantId()
        );
        deleteEvents(current.linkedActivityIds(), principal.tenantId());

        deletedRecords += deleteByIds("stock_sku_change_requests", ids, principal.tenantId());
        deletedRecords += deleteByIds("stock_count_submissions", ids, principal.tenantId());
        deletedRecords += deleteByIds("stock_receivables", ids, principal.tenantId());
        deletedRecords += deleteByIds("business_reports", ids, principal.tenantId());
        deletedRecords += deleteByIds("task_records", ids, principal.tenantId());
        deletedRecords += deleteByIds("attendance_events", ids, principal.tenantId());
        deletedRecords += deleteByIds("knowledge_sop_watch_sessions", ids, principal.tenantId());
        deletedRecords += deleteByIds("advertisements", ids, principal.tenantId());
        deletedRecords += deleteByIds("stock_skus", ids, principal.tenantId());
        deletedRecords += deleteByIds("stock_suppliers", ids, principal.tenantId());
        deletedRecords += deleteByIds("task_templates", ids, principal.tenantId());
        deletedRecords += deleteByIds("stock_tags", ids, principal.tenantId());

        if (deletedRecords != run.recordCount()) {
            throw conflict(
                    "BUSINESS_CLEANUP_DELETE_MISMATCH",
                    "The cleanup selection changed while deleting. No records were removed; create a fresh backup."
            );
        }

        long deletedPhotos = deleteOrphanedCandidateMedia(current.allMedia(), principal.tenantId());
        Instant completedAt = Instant.now();
        jdbcTemplate.update(
                """
                update business_cleanup_runs
                set status = 'COMPLETED', completed_at = :completedAt,
                    deleted_record_count = :deletedRecords,
                    deleted_photo_count = :deletedPhotos
                where id = :id and tenant_id = :tenantId
                """,
                runParameters(principal, runId)
                        .addValue("completedAt", completedAt)
                        .addValue("deletedRecords", deletedRecords)
                        .addValue("deletedPhotos", deletedPhotos)
        );
        return new BusinessCleanupCompleteResponse(runId, deletedRecords, deletedPhotos, completedAt);
    }

    private Snapshot snapshot(
            AuthenticatedUser principal,
            BusinessCleanupRequest request,
            Instant asOf
    ) {
        MapSqlParameterSource parameters = baseParameters(principal, request, asOf);
        List<RecordRef> refs = new ArrayList<>();
        EnumMap<BusinessCleanupDataType, Long> blocked = new EnumMap<>(BusinessCleanupDataType.class);

        for (BusinessCleanupDataType type : BusinessCleanupDataType.values()) {
            blocked.put(type, 0L);
        }
        if (request.dataTypes().contains(BusinessCleanupDataType.INACTIVE_SETUP)) {
            addRefs(refs, BusinessCleanupDataType.INACTIVE_SETUP, "stock_skus", "sku", SKU_ELIGIBLE, parameters);
            addRefs(refs, BusinessCleanupDataType.INACTIVE_SETUP, "stock_suppliers", "supplier", SUPPLIER_ELIGIBLE, parameters);
            addRefs(refs, BusinessCleanupDataType.INACTIVE_SETUP, "stock_tags", "tag", TAG_ELIGIBLE, parameters);
            addRefs(refs, BusinessCleanupDataType.INACTIVE_SETUP, "task_templates", "template", TASK_TEMPLATE_ELIGIBLE, parameters);
            addRefs(refs, BusinessCleanupDataType.INACTIVE_SETUP, "advertisements", "advertisement", ADVERTISEMENT_ELIGIBLE, parameters);
            long inactive = count("stock_skus", "sku", "sku.tenant_id = :tenantId and not sku.active and sku.updated_at::date < :cutoffDate and sku.updated_at <= :asOf", parameters)
                    + count("stock_suppliers", "supplier", "supplier.tenant_id = :tenantId and not supplier.active and supplier.updated_at::date < :cutoffDate and supplier.updated_at <= :asOf", parameters)
                    + count("stock_tags", "tag", "tag.tenant_id = :tenantId and not tag.active and tag.updated_at::date < :cutoffDate and tag.updated_at <= :asOf", parameters)
                    + count("task_templates", "template", "template.tenant_id = :tenantId and not template.active and template.updated_at::date < :cutoffDate and template.updated_at <= :asOf", parameters);
            long safelyInactive = refs.stream()
                    .filter(ref -> ref.dataType() == BusinessCleanupDataType.INACTIVE_SETUP)
                    .filter(ref -> !ref.tableName().equals("advertisements"))
                    .count();
            blocked.put(BusinessCleanupDataType.INACTIVE_SETUP, inactive - safelyInactive);
        }
        if (request.dataTypes().contains(BusinessCleanupDataType.STOCK_HISTORY)) {
            addRefs(refs, BusinessCleanupDataType.STOCK_HISTORY, "stock_sku_change_requests", "request", SKU_REQUEST_ELIGIBLE, parameters);
            addRefs(refs, BusinessCleanupDataType.STOCK_HISTORY, "stock_count_submissions", "count_record", COUNT_ELIGIBLE, parameters);
            addRefs(refs, BusinessCleanupDataType.STOCK_HISTORY, "stock_receivables", "receivable", RECEIVABLE_ELIGIBLE, parameters);
        }
        if (request.dataTypes().contains(BusinessCleanupDataType.REPORTS)) {
            addRefs(refs, BusinessCleanupDataType.REPORTS, "business_reports", "report", REPORT_ELIGIBLE, parameters);
        }
        if (request.dataTypes().contains(BusinessCleanupDataType.TASKS)) {
            addRefs(refs, BusinessCleanupDataType.TASKS, "task_records", "task", TASK_ELIGIBLE, parameters);
        }
        if (request.dataTypes().contains(BusinessCleanupDataType.ATTENDANCE)) {
            addRefs(refs, BusinessCleanupDataType.ATTENDANCE, "attendance_events", "attendance", ATTENDANCE_ELIGIBLE, parameters);
        }
        if (request.dataTypes().contains(BusinessCleanupDataType.ACTIVITY)) {
            addRefs(refs, BusinessCleanupDataType.ACTIVITY, "activity_events", "event", ACTIVITY_ELIGIBLE, parameters);
        }
        if (request.dataTypes().contains(BusinessCleanupDataType.VIDEO_ANALYTICS)) {
            addRefs(refs, BusinessCleanupDataType.VIDEO_ANALYTICS, "knowledge_sop_watch_sessions", "watch", VIDEO_ELIGIBLE, parameters);
        }

        List<MediaFile> associations = mediaAssociations(principal.tenantId(), refs, false);
        List<MediaFile> allMedia = distinctMedia(associations);
        List<MediaFile> selectedMedia = distinctMedia(
                associations.stream()
                        .filter(media -> selectedMediaTypes(request).contains(media.mediaType()))
                        .toList()
        );
        List<BusinessCleanupPreviewResponse.Category> categories = request.dataTypes().stream()
                .sorted()
                .map(type -> new BusinessCleanupPreviewResponse.Category(
                        type,
                        refs.stream().filter(ref -> ref.dataType() == type).count(),
                        blocked.get(type),
                        DESCRIPTIONS.get(type)
                ))
                .toList();
        long blockedCount = blocked.values().stream().mapToLong(Long::longValue).sum();
        long mediaBytes = selectedMedia.stream().mapToLong(MediaFile::sizeBytes).sum();
        long estimatedBytes = mediaBytes + Math.max(4096, refs.size() * 1024L);
        BusinessCleanupPreviewResponse response = new BusinessCleanupPreviewResponse(
                refs.size(),
                blockedCount,
                selectedMedia.size(),
                allMedia.size() - selectedMedia.size(),
                estimatedBytes,
                categories
        );
        LinkSnapshot links = linkSnapshot(principal.tenantId(), refs);
        String fingerprint = selectionFingerprint(refs, allMedia, links);
        return new Snapshot(
                List.copyOf(refs),
                allMedia,
                links.linkedActivityIds(),
                links.notificationIds(),
                links.pushOutboxIds(),
                response,
                fingerprint
        );
    }

    private byte[] createZip(
            UUID runId,
            AuthenticatedUser principal,
            BusinessCleanupRequest request,
            Snapshot snapshot,
            Instant createdAt
    ) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("format", "EASTAPP_BUSINESS_BACKUP");
            manifest.put("formatVersion", 1);
            manifest.put("runId", runId);
            manifest.put("tenantId", principal.tenantId());
            manifest.put("tenantCode", principal.tenantCode());
            manifest.put("createdAt", createdAt);
            manifest.put("cutoffDate", request.cutoffDate());
            manifest.put("dataTypes", request.dataTypes().stream().sorted().map(Enum::name).toList());
            manifest.put("mediaMode", request.mediaMode().name());
            manifest.put("mediaTypes", selectedMediaTypes(request).stream().sorted().map(Enum::name).toList());
            manifest.put("preview", snapshot.response());
            manifest.put("selectionSha256", snapshot.selectionSha256());
            manifest.put("warning", "Photos excluded from this ZIP may be permanently deleted during cleanup when no retained record references them.");
            writeJson(zip, "manifest.json", manifest);

            Map<String, List<UUID>> ids = snapshot.recordRefs().stream()
                    .collect(Collectors.groupingBy(
                            RecordRef::tableName,
                            LinkedHashMap::new,
                            Collectors.mapping(RecordRef::id, Collectors.toList())
                    ));
            writeBusinessData(zip, principal.tenantId(), ids, snapshot);

            Set<BusinessCleanupMediaType> mediaTypes = selectedMediaTypes(request);
            List<MediaFile> photos = distinctMedia(
                    mediaAssociations(principal.tenantId(), snapshot.recordRefs(), true).stream()
                            .filter(media -> mediaTypes.contains(media.mediaType()))
                            .toList()
            );
            for (MediaFile photo : photos) {
                String path = "photos/" + photo.mediaType().name().toLowerCase(Locale.ROOT)
                        + "/" + photo.id() + extension(photo.contentType());
                writeBytes(zip, path, photo.bytes());
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BUSINESS_BACKUP_CREATE_FAILED",
                    "The business backup ZIP could not be generated."
            );
        }
    }

    private void writeBusinessData(
            ZipOutputStream zip,
            UUID tenantId,
            Map<String, List<UUID>> ids,
            Snapshot snapshot
    ) throws IOException {
        MapSqlParameterSource tenant = new MapSqlParameterSource("tenantId", tenantId);
        addTenantTable(zip, "inactive_setup", "stock_skus", ids, tenant);
        addByParent(zip, "inactive_setup", "stock_sku_suppliers", "sku_id", ids.get("stock_skus"));
        addByParent(zip, "inactive_setup", "stock_sku_assignees", "sku_id", ids.get("stock_skus"));
        addByParent(zip, "inactive_setup", "stock_sku_receivable_checklist", "sku_id", ids.get("stock_skus"));
        addTenantTable(zip, "inactive_setup", "stock_suppliers", ids, tenant);
        addTenantTable(zip, "inactive_setup", "stock_tags", ids, tenant);
        addTenantChild(zip, "inactive_setup", "stock_tag_assignees", "tag_id", ids.get("stock_tags"), tenantId);
        addTenantTable(zip, "inactive_setup", "task_templates", ids, tenant);
        addTenantChild(zip, "inactive_setup", "task_template_checklist_items", "template_id", ids.get("task_templates"), tenantId);
        addTenantTable(zip, "inactive_setup", "advertisements", ids, tenant);

        addTenantTable(zip, "stock_history", "stock_sku_change_requests", ids, tenant);
        addTenantTable(zip, "stock_history", "stock_count_submissions", ids, tenant);
        addByParent(zip, "stock_history", "stock_count_submission_checks", "submission_id", ids.get("stock_count_submissions"));
        addByParent(zip, "stock_history", "stock_count_submission_remarks", "submission_id", ids.get("stock_count_submissions"));
        addTenantTable(zip, "stock_history", "stock_receivables", ids, tenant);
        addByParent(zip, "stock_history", "stock_receivable_items", "receivable_id", ids.get("stock_receivables"));

        addTenantTable(zip, "reports", "business_reports", ids, tenant);
        addTenantChild(zip, "reports", "sales_report_details", "report_id", ids.get("business_reports"), tenantId);
        addTenantChild(zip, "reports", "sales_void_bills", "sales_report_id", ids.get("business_reports"), tenantId);
        addTenantChild(zip, "reports", "waste_report_details", "report_id", ids.get("business_reports"), tenantId);
        addTenantChild(zip, "reports", "daily_report_photos", "report_id", ids.get("business_reports"), tenantId);
        addTenantChild(zip, "reports", "complaint_report_details", "report_id", ids.get("business_reports"), tenantId);

        addTenantTable(zip, "tasks", "task_records", ids, tenant);
        addTenantChild(zip, "tasks", "task_record_checklist_items", "record_id", ids.get("task_records"), tenantId);
        addTenantChild(zip, "tasks", "task_photos", "record_id", ids.get("task_records"), tenantId);
        addTenantTable(zip, "attendance", "attendance_events", ids, tenant);
        addTenantTable(zip, "activity", "activity_events", ids, tenant);
        addTenantTable(zip, "video_analytics", "knowledge_sop_watch_sessions", ids, tenant);

        addActivityDependants(zip, "activity", ids.get("activity_events"), snapshot, tenantId);
        if (!snapshot.linkedActivityIds().isEmpty()) {
            writeJson(
                    zip,
                    "data/linked_activity/activity_events.json",
                    rows(
                            "select * from activity_events where tenant_id = :tenantId and id in (:eventIds)",
                            new MapSqlParameterSource()
                                    .addValue("tenantId", tenantId)
                                    .addValue("eventIds", snapshot.linkedActivityIds())
                    )
            );
            addActivityDependants(
                    zip,
                    "linked_activity",
                    snapshot.linkedActivityIds(),
                    snapshot,
                    tenantId
            );
        }
    }

    private void addActivityDependants(
            ZipOutputStream zip,
            String folder,
            List<UUID> eventIds,
            Snapshot snapshot,
            UUID tenantId
    ) throws IOException {
        if (eventIds == null || eventIds.isEmpty()) return;
        if (snapshot.notificationIds().isEmpty()) return;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("eventIds", eventIds)
                .addValue("notificationIds", snapshot.notificationIds());
        List<Map<String, Object>> notifications = rows(
                "select * from user_notifications where tenant_id = :tenantId and activity_event_id in (:eventIds) and id in (:notificationIds)",
                parameters
        );
        writeJson(zip, "data/" + folder + "/user_notifications.json", notifications);
        List<UUID> notificationIds = ids(notifications);
        if (!notificationIds.isEmpty() && !snapshot.pushOutboxIds().isEmpty()) {
            writeJson(
                    zip,
                    "data/" + folder + "/push_outbox.json",
                    rows(
                            "select * from push_outbox where notification_id in (:notificationIds) and id in (:pushIds)",
                            new MapSqlParameterSource()
                                    .addValue("notificationIds", notificationIds)
                                    .addValue("pushIds", snapshot.pushOutboxIds())
                    )
            );
        }
    }

    private void addTenantTable(
            ZipOutputStream zip,
            String folder,
            String table,
            Map<String, List<UUID>> ids,
            MapSqlParameterSource tenant
    ) throws IOException {
        List<UUID> selected = ids.get(table);
        if (selected == null || selected.isEmpty()) return;
        MapSqlParameterSource parameters = new MapSqlParameterSource(tenant.getValues())
                .addValue("ids", selected);
        writeJson(
                zip,
                "data/" + folder + "/" + table + ".json",
                rows("select * from " + table + " where tenant_id = :tenantId and id in (:ids)", parameters)
        );
    }

    private void addTenantChild(
            ZipOutputStream zip,
            String folder,
            String table,
            String parentColumn,
            List<UUID> parentIds,
            UUID tenantId
    ) throws IOException {
        if (parentIds == null || parentIds.isEmpty()) return;
        writeJson(
                zip,
                "data/" + folder + "/" + table + ".json",
                rows(
                        "select * from " + table + " where tenant_id = :tenantId and " + parentColumn + " in (:parentIds)",
                        new MapSqlParameterSource()
                                .addValue("tenantId", tenantId)
                                .addValue("parentIds", parentIds)
                )
        );
    }

    private void addByParent(
            ZipOutputStream zip,
            String folder,
            String table,
            String parentColumn,
            List<UUID> parentIds
    ) throws IOException {
        if (parentIds == null || parentIds.isEmpty()) return;
        writeJson(
                zip,
                "data/" + folder + "/" + table + ".json",
                rows(
                        "select * from " + table + " where " + parentColumn + " in (:parentIds)",
                        new MapSqlParameterSource("parentIds", parentIds)
                )
        );
    }

    private List<MediaFile> mediaAssociations(
            UUID tenantId,
            List<RecordRef> refs,
            boolean includeBytes
    ) {
        Map<String, List<UUID>> ids = refs.stream().collect(Collectors.groupingBy(
                RecordRef::tableName,
                Collectors.mapping(RecordRef::id, Collectors.toList())
        ));
        List<MediaFile> media = new ArrayList<>();
        String projection = "media.id, media.storage_key, media.content_type, media.size_bytes"
                + (includeBytes ? ", media.content_bytes" : "");

        addMedia(media, "stock", BusinessCleanupMediaType.SKU_THUMBNAILS,
                "select " + projection + " from stock_media media join stock_skus sku on sku.tenant_id = media.tenant_id and sku.thumbnail_media_id = media.id where media.tenant_id = :tenantId and sku.id in (:parentIds)",
                tenantId, ids.get("stock_skus"), includeBytes);
        addMedia(media, "stock", BusinessCleanupMediaType.SKU_THUMBNAILS,
                "select distinct " + projection + " from stock_media media join stock_sku_change_requests request on request.tenant_id = media.tenant_id and request.payload_json::jsonb ->> 'photoPath' = media.storage_key where media.tenant_id = :tenantId and request.id in (:parentIds)",
                tenantId, ids.get("stock_sku_change_requests"), includeBytes);
        addMedia(media, "stock", BusinessCleanupMediaType.STOCK_COUNT_PHOTOS,
                "select distinct " + projection + " from stock_media media join stock_count_submissions count_record on count_record.tenant_id = media.tenant_id and (count_record.stock_photo_name = media.storage_key or count_record.invoice_photo_name = media.storage_key) where media.tenant_id = :tenantId and count_record.id in (:parentIds)",
                tenantId, ids.get("stock_count_submissions"), includeBytes);
        addMedia(media, "stock", BusinessCleanupMediaType.RECEIVABLE_INVOICE_PHOTOS,
                "select distinct " + projection + " from stock_media media join stock_receivables receivable on receivable.tenant_id = media.tenant_id and receivable.invoice_photo_name = media.storage_key where media.tenant_id = :tenantId and receivable.id in (:parentIds)",
                tenantId, ids.get("stock_receivables"), includeBytes);
        addMedia(media, "stock", BusinessCleanupMediaType.RECEIVABLE_GOODS_PHOTOS,
                "select distinct " + projection + " from stock_media media join stock_receivables receivable on receivable.tenant_id = media.tenant_id and receivable.goods_photo_name = media.storage_key where media.tenant_id = :tenantId and receivable.id in (:parentIds)",
                tenantId, ids.get("stock_receivables"), includeBytes);
        addMedia(media, "report", BusinessCleanupMediaType.SALES_VOID_BILL_PHOTOS,
                "select distinct " + projection + " from report_media media join sales_void_bills detail on detail.tenant_id = media.tenant_id and detail.photo_media_id = media.id where media.tenant_id = :tenantId and detail.sales_report_id in (:parentIds)",
                tenantId, ids.get("business_reports"), includeBytes);
        addMedia(media, "report", BusinessCleanupMediaType.WASTE_PHOTOS,
                "select distinct " + projection + " from report_media media join waste_report_details detail on detail.tenant_id = media.tenant_id and detail.photo_media_id = media.id where media.tenant_id = :tenantId and detail.report_id in (:parentIds)",
                tenantId, ids.get("business_reports"), includeBytes);
        addMedia(media, "report", BusinessCleanupMediaType.COMPLAINT_PHOTOS,
                "select distinct " + projection + " from report_media media join complaint_report_details detail on detail.tenant_id = media.tenant_id and detail.photo_media_id = media.id where media.tenant_id = :tenantId and detail.report_id in (:parentIds)",
                tenantId, ids.get("business_reports"), includeBytes);
        addMedia(media, "report", BusinessCleanupMediaType.TASK_REPORT_PHOTOS,
                "select distinct " + projection + " from report_media media join daily_report_photos detail on detail.tenant_id = media.tenant_id and detail.photo_media_id = media.id where media.tenant_id = :tenantId and detail.report_id in (:parentIds)",
                tenantId, ids.get("business_reports"), includeBytes);
        addMedia(media, "report", BusinessCleanupMediaType.TASK_REPORT_PHOTOS,
                "select distinct " + projection + " from report_media media join task_photos detail on detail.tenant_id = media.tenant_id and detail.photo_media_id = media.id where media.tenant_id = :tenantId and detail.record_id in (:parentIds)",
                tenantId, ids.get("task_records"), includeBytes);
        addMedia(media, "report", BusinessCleanupMediaType.ADVERTISEMENT_PHOTOS,
                "select distinct " + projection + " from report_media media join advertisements advertisement on advertisement.tenant_id = media.tenant_id and advertisement.image_storage_key = media.storage_key where media.tenant_id = :tenantId and advertisement.id in (:parentIds)",
                tenantId, ids.get("advertisements"), includeBytes);
        return media;
    }

    private void addMedia(
            List<MediaFile> target,
            String source,
            BusinessCleanupMediaType mediaType,
            String sql,
            UUID tenantId,
            List<UUID> parentIds,
            boolean includeBytes
    ) {
        if (parentIds == null || parentIds.isEmpty()) return;
        target.addAll(jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("parentIds", parentIds),
                (resultSet, rowNumber) -> new MediaFile(
                        source,
                        resultSet.getObject("id", UUID.class),
                        mediaType,
                        resultSet.getString("storage_key"),
                        resultSet.getString("content_type"),
                        resultSet.getLong("size_bytes"),
                        includeBytes ? resultSet.getBytes("content_bytes") : null
                )
        ));
    }

    private LinkSnapshot linkSnapshot(UUID tenantId, List<RecordRef> refs) {
        List<UUID> mainEventIds = refs.stream()
                .filter(ref -> ref.tableName().equals("activity_events"))
                .map(RecordRef::id)
                .toList();
        List<UUID> targetIds = refs.stream()
                .filter(ref -> !ref.tableName().equals("activity_events"))
                .map(RecordRef::id)
                .distinct()
                .toList();
        List<UUID> linkedEventIds = targetIds.isEmpty()
                ? List.of()
                : jdbcTemplate.query(
                        "select id from activity_events where tenant_id = :tenantId and target_id in (:targetIds)",
                        new MapSqlParameterSource()
                                .addValue("tenantId", tenantId)
                                .addValue("targetIds", targetIds),
                        (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class)
                ).stream().filter(id -> !mainEventIds.contains(id)).distinct().toList();
        List<UUID> allEventIds = new ArrayList<>(mainEventIds);
        allEventIds.addAll(linkedEventIds);
        List<UUID> notificationIds = allEventIds.isEmpty()
                ? List.of()
                : jdbcTemplate.query(
                        "select id from user_notifications where tenant_id = :tenantId and activity_event_id in (:eventIds)",
                        new MapSqlParameterSource()
                                .addValue("tenantId", tenantId)
                                .addValue("eventIds", allEventIds),
                        (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class)
                ).stream().distinct().toList();
        List<UUID> pushIds = notificationIds.isEmpty()
                ? List.of()
                : jdbcTemplate.query(
                        "select id from push_outbox where notification_id in (:notificationIds)",
                        new MapSqlParameterSource("notificationIds", notificationIds),
                        (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class)
                ).stream().distinct().toList();
        return new LinkSnapshot(linkedEventIds, notificationIds, pushIds);
    }

    private long deleteEvents(List<UUID> eventIds, UUID tenantId) {
        if (eventIds.isEmpty()) return 0;
        return jdbcTemplate.update(
                "delete from activity_events where tenant_id = :tenantId and id in (:ids)",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("ids", eventIds)
        );
    }

    private void deleteNotifications(List<UUID> notificationIds, UUID tenantId) {
        if (notificationIds.isEmpty()) return;
        jdbcTemplate.update(
                "delete from user_notifications where tenant_id = :tenantId and id in (:ids)",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("ids", notificationIds)
        );
    }

    private long deleteByIds(String table, Map<String, List<UUID>> ids, UUID tenantId) {
        List<UUID> selected = ids.get(table);
        if (selected == null || selected.isEmpty()) return 0;
        return jdbcTemplate.update(
                "delete from " + table + " where tenant_id = :tenantId and id in (:ids)",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("ids", selected)
        );
    }

    private long deleteOrphanedCandidateMedia(List<MediaFile> media, UUID tenantId) {
        List<UUID> stockIds = media.stream()
                .filter(item -> item.source().equals("stock"))
                .map(MediaFile::id)
                .distinct()
                .toList();
        List<UUID> reportIds = media.stream()
                .filter(item -> item.source().equals("report"))
                .map(MediaFile::id)
                .distinct()
                .toList();
        long deleted = 0;
        if (!stockIds.isEmpty()) {
            deleted += jdbcTemplate.update(
                    "delete from stock_media candidate where candidate.tenant_id = :tenantId and candidate.id in (:ids) and " + UNUSED_STOCK_MEDIA,
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("ids", stockIds)
            );
        }
        if (!reportIds.isEmpty()) {
            deleted += jdbcTemplate.update(
                    "delete from report_media candidate where candidate.tenant_id = :tenantId and candidate.id in (:ids) and " + UNUSED_REPORT_MEDIA,
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("ids", reportIds)
            );
        }
        return deleted;
    }

    private void addRefs(
            List<RecordRef> target,
            BusinessCleanupDataType dataType,
            String table,
            String alias,
            String condition,
            MapSqlParameterSource parameters
    ) {
        target.addAll(jdbcTemplate.query(
                "select " + alias + ".id from " + table + " " + alias + " where " + condition,
                parameters,
                (resultSet, rowNumber) -> new RecordRef(
                        dataType,
                        table,
                        resultSet.getObject("id", UUID.class)
                )
        ));
    }

    private long count(
            String table,
            String alias,
            String condition,
            MapSqlParameterSource parameters
    ) {
        Long value = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " " + alias + " where " + condition,
                parameters,
                Long.class
        );
        return value == null ? 0 : value;
    }

    private List<Map<String, Object>> rows(String sql, MapSqlParameterSource parameters) {
        return jdbcTemplate.queryForList(sql, parameters).stream()
                .map(this::normaliseRow)
                .toList();
    }

    private Map<String, Object> normaliseRow(Map<String, Object> row) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> result.put(key, normalise(value)));
        return result;
    }

    private Object normalise(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().toString();
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        return value.toString();
    }

    private void writeJson(ZipOutputStream zip, String path, Object value) throws IOException {
        writeBytes(zip, path, jsonMapper.writeValueAsBytes(value));
    }

    private static void writeBytes(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static List<UUID> ids(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> row.get("id"))
                .filter(value -> value != null)
                .map(value -> UUID.fromString(value.toString()))
                .toList();
    }

    private RunRow run(AuthenticatedUser principal, UUID runId) {
        List<RunRow> rows = jdbcTemplate.query(
                "select * from business_cleanup_runs where id = :id and tenant_id = :tenantId",
                runParameters(principal, runId),
                (resultSet, rowNumber) -> new RunRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("tenant_id", UUID.class),
                        resultSet.getObject("requested_by_user_id", UUID.class),
                        resultSet.getObject("cutoff_date", LocalDate.class),
                        resultSet.getString("data_types"),
                        resultSet.getString("media_mode"),
                        resultSet.getString("media_types"),
                        resultSet.getLong("record_count"),
                        resultSet.getLong("blocked_record_count"),
                        resultSet.getLong("photo_count"),
                        resultSet.getLong("excluded_photo_count"),
                        resultSet.getLong("estimated_zip_bytes"),
                        resultSet.getString("selection_sha256"),
                        resultSet.getString("zip_file_name"),
                        resultSet.getLong("zip_size_bytes"),
                        resultSet.getString("zip_sha256"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        instant(resultSet.getTimestamp("saved_confirmed_at")),
                        instant(resultSet.getTimestamp("completed_at")),
                        resultSet.getLong("deleted_record_count"),
                        resultSet.getLong("deleted_photo_count")
                )
        );
        if (rows.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "BUSINESS_CLEANUP_RUN_NOT_FOUND",
                    "The backup and cleanup record was not found."
            );
        }
        return rows.getFirst();
    }

    private BusinessCleanupRunResponse runResponse(RunRow run) {
        return new BusinessCleanupRunResponse(
                run.id(),
                run.cutoffDate(),
                parseEnums(run.dataTypes(), BusinessCleanupDataType::valueOf),
                BusinessCleanupMediaMode.valueOf(run.mediaMode()),
                parseEnums(run.mediaTypes(), BusinessCleanupMediaType::valueOf),
                run.recordCount(),
                run.blockedRecordCount(),
                run.photoCount(),
                run.excludedPhotoCount(),
                run.zipSizeBytes(),
                run.zipFileName(),
                run.zipSha256(),
                run.status(),
                run.createdAt(),
                run.savedConfirmedAt(),
                run.completedAt(),
                run.deletedRecordCount(),
                run.deletedPhotoCount()
        );
    }

    private BusinessCleanupRequest request(RunRow run) {
        return new BusinessCleanupRequest(
                parseEnums(run.dataTypes(), BusinessCleanupDataType::valueOf),
                run.cutoffDate(),
                BusinessCleanupMediaMode.valueOf(run.mediaMode()),
                parseEnums(run.mediaTypes(), BusinessCleanupMediaType::valueOf)
        );
    }

    private static void validate(BusinessCleanupRequest request) {
        if (request.mediaMode() == BusinessCleanupMediaMode.SELECTED && request.mediaTypes().isEmpty()) {
            throw badRequest(
                    "BUSINESS_CLEANUP_MEDIA_TYPES_REQUIRED",
                    "Choose at least one photo type, or select data only."
            );
        }
    }

    private static Set<BusinessCleanupMediaType> selectedMediaTypes(BusinessCleanupRequest request) {
        return switch (request.mediaMode()) {
            case ALL -> EnumSet.allOf(BusinessCleanupMediaType.class);
            case SELECTED -> EnumSet.copyOf(request.mediaTypes());
            case NONE -> Set.of();
        };
    }

    private static List<MediaFile> distinctMedia(List<MediaFile> media) {
        return List.copyOf(media.stream().collect(Collectors.toMap(
                item -> item.source() + ':' + item.id(),
                Function.identity(),
                (first, duplicate) -> first,
                LinkedHashMap::new
        )).values());
    }

    private static String selectionFingerprint(
            List<RecordRef> refs,
            List<MediaFile> media,
            LinkSnapshot links
    ) {
        String value = refs.stream()
                .map(ref -> "record:" + ref.tableName() + ':' + ref.id())
                .sorted()
                .collect(Collectors.joining("\n"));
        String photos = media.stream()
                .map(item -> "photo:" + item.source() + ':' + item.id() + ':' + item.sizeBytes())
                .sorted()
                .collect(Collectors.joining("\n"));
        String linked = links.linkedActivityIds().stream()
                .map(id -> "link:activity:" + id)
                .sorted()
                .collect(Collectors.joining("\n"));
        String notifications = links.notificationIds().stream()
                .map(id -> "link:notification:" + id)
                .sorted()
                .collect(Collectors.joining("\n"));
        String pushes = links.pushOutboxIds().stream()
                .map(id -> "link:push:" + id)
                .sorted()
                .collect(Collectors.joining("\n"));
        return sha256((value + "\n" + photos + "\n" + linked + "\n"
                + notifications + "\n" + pushes).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String enumText(Set<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private static <T extends Enum<T>> Set<T> parseEnums(
            String value,
            Function<String, T> parser
    ) {
        if (value == null || value.isBlank()) return Set.of();
        return Set.copyOf(Arrays.stream(value.split(",")).map(parser).toList());
    }

    private static String fileName(AuthenticatedUser principal, Instant createdAt) {
        String tenant = principal.tenantCode().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return "eastapp-business-backup-" + tenant + '-'
                + FILE_TIME.format(createdAt.atZone(BUSINESS_ZONE)) + ".zip";
    }

    private static String extension(String contentType) {
        if (contentType == null) return ".bin";
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic", "image/heif" -> ".heic";
            default -> ".bin";
        };
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static MapSqlParameterSource baseParameters(
            AuthenticatedUser principal,
            BusinessCleanupRequest request,
            Instant asOf
    ) {
        return new MapSqlParameterSource()
                .addValue("tenantId", principal.tenantId())
                .addValue("cutoffDate", request.cutoffDate())
                .addValue("asOf", asOf);
    }

    private static MapSqlParameterSource runParameters(AuthenticatedUser principal, UUID runId) {
        return new MapSqlParameterSource()
                .addValue("tenantId", principal.tenantId())
                .addValue("id", runId);
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public record BackupFile(UUID runId, String fileName, String sha256, byte[] bytes) {
    }

    private record Snapshot(
            List<RecordRef> recordRefs,
            List<MediaFile> allMedia,
            List<UUID> linkedActivityIds,
            List<UUID> notificationIds,
            List<UUID> pushOutboxIds,
            BusinessCleanupPreviewResponse response,
            String selectionSha256
    ) {
    }

    private record LinkSnapshot(
            List<UUID> linkedActivityIds,
            List<UUID> notificationIds,
            List<UUID> pushOutboxIds
    ) {
    }

    private record RecordRef(
            BusinessCleanupDataType dataType,
            String tableName,
            UUID id
    ) {
    }

    private record MediaFile(
            String source,
            UUID id,
            BusinessCleanupMediaType mediaType,
            String storageKey,
            String contentType,
            long sizeBytes,
            byte[] bytes
    ) {
    }

    private record RunRow(
            UUID id,
            UUID tenantId,
            UUID requestedByUserId,
            LocalDate cutoffDate,
            String dataTypes,
            String mediaMode,
            String mediaTypes,
            long recordCount,
            long blockedRecordCount,
            long photoCount,
            long excludedPhotoCount,
            long estimatedZipBytes,
            String selectionSha256,
            String zipFileName,
            long zipSizeBytes,
            String zipSha256,
            String status,
            Instant createdAt,
            Instant savedConfirmedAt,
            Instant completedAt,
            long deletedRecordCount,
            long deletedPhotoCount
    ) {
    }
}
