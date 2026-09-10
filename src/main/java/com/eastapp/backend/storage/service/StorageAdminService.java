package com.eastapp.backend.storage.service;

import com.eastapp.backend.activity.service.NotificationRetentionCleanup;
import com.eastapp.backend.auth.LoginIdentityRepository;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.organisation.TenantRepository;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.storage.api.StorageCleanupResponse;
import com.eastapp.backend.storage.api.StorageOverviewResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StorageAdminService {
    private static final String ADMIN_EMPLOYEE_ID = "E0001";
    private static final String ADMIN_PHONE = "+60166016488";
    private static final int RETENTION_DAYS = 30;

    private static final List<CleanupDefinition> CLEANUP_DEFINITIONS = List.of(
            new CleanupDefinition(
                    "activity",
                    "Activity & notifications",
                    "Notifications and activity events older than 30 days.",
                    Set.of("activity_events", "user_notifications", "push_outbox"),
                    true
            ),
            new CleanupDefinition(
                    "attendance",
                    "Attendance history",
                    "Attendance scans and expired QR codes older than 30 days.",
                    Set.of("attendance_events", "attendance_qr_codes"),
                    false
            ),
            new CleanupDefinition(
                    "stock-counts",
                    "Stock count history",
                    "Daily count submissions, checks, remarks and unused photos older than 30 days.",
                    Set.of(
                            "stock_count_submissions",
                            "stock_count_submission_checks",
                            "stock_count_submission_remarks",
                            "stock_media"
                    ),
                    false
            ),
            new CleanupDefinition(
                    "receiving",
                    "Receiving history",
                    "Receiving records, items and unused photos older than 30 days.",
                    Set.of("stock_receivings", "stock_receiving_items", "stock_media"),
                    false
            ),
            new CleanupDefinition(
                    "tasks",
                    "Task history",
                    "Task records, checklist results and unused task photos older than 30 days.",
                    Set.of("task_records", "task_record_checklist_items", "task_photos", "report_media"),
                    false
            ),
            new CleanupDefinition(
                    "reports",
                    "Report history",
                    "Business reports, details and unused report photos older than 30 days.",
                    Set.of(
                            "business_reports",
                            "sales_report_details",
                            "sales_void_bills",
                            "waste_report_details",
                            "daily_report_photos",
                            "complaint_report_details",
                            "report_media"
                    ),
                    false
            ),
            new CleanupDefinition(
                    "video-analytics",
                    "Video analytics",
                    "SOP viewing sessions last updated more than 30 days ago.",
                    Set.of("knowledge_sop_watch_sessions"),
                    false
            ),
            new CleanupDefinition(
                    "sku-approvals",
                    "Completed SKU approvals",
                    "Completed SKU create, edit and delete requests older than 30 days.",
                    Set.of("stock_sku_change_requests"),
                    false
            ),
            new CleanupDefinition(
                    "unused-media",
                    "Unused uploads",
                    "Uploaded images older than 30 days that are not used by any current record.",
                    Set.of("stock_media", "report_media"),
                    false
            )
    );

    private final JdbcTemplate jdbcTemplate;
    private final LoginIdentityRepository loginIdentityRepository;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationRetentionCleanup activityRetentionCleanup;

    public StorageAdminService(
            JdbcTemplate jdbcTemplate,
            LoginIdentityRepository loginIdentityRepository,
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            NotificationRetentionCleanup activityRetentionCleanup
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.loginIdentityRepository = loginIdentityRepository;
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.activityRetentionCleanup = activityRetentionCleanup;
    }

    @Transactional(readOnly = true)
    public StorageOverviewResponse overview(AuthenticatedUser principal) {
        assertStorageAdmin(principal);
        List<StorageOverviewResponse.TableUsage> tables = loadTableUsage();
        long databaseBytes = jdbcTemplate.queryForObject(
                "select pg_database_size(current_database())",
                Long.class
        );
        long applicationBytes = tables.stream()
                .mapToLong(StorageOverviewResponse.TableUsage::totalBytes)
                .sum();
        Map<String, Long> tableBytes = tables.stream().collect(
                java.util.stream.Collectors.toMap(
                        StorageOverviewResponse.TableUsage::tableName,
                        StorageOverviewResponse.TableUsage::totalBytes
                )
        );
        List<StorageOverviewResponse.CleanupAction> actions = CLEANUP_DEFINITIONS.stream()
                .map(definition -> new StorageOverviewResponse.CleanupAction(
                        definition.key(),
                        definition.title(),
                        definition.description(),
                        RETENTION_DAYS,
                        definition.tables().stream().mapToLong(
                                table -> tableBytes.getOrDefault(table, 0L)
                        ).sum(),
                        definition.automatic()
                ))
                .toList();
        return new StorageOverviewResponse(
                Instant.now(),
                databaseBytes,
                applicationBytes,
                tables,
                actions
        );
    }

    @Transactional
    public StorageCleanupResponse cleanup(
            AuthenticatedUser principal,
            String rawKey,
            boolean confirmed
    ) {
        assertStorageAdmin(principal);
        if (!confirmed) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CLEANUP_CONFIRMATION_REQUIRED",
                    "Confirm the permanent deletion first."
            );
        }

        CleanupDefinition definition = CLEANUP_DEFINITIONS.stream()
                .filter(item -> item.key().equals(rawKey))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "CLEANUP_CATEGORY_NOT_FOUND",
                        "Storage cleanup category not found."
                ));

        Instant now = Instant.now();
        Instant cutoff = now.minus(java.time.Duration.ofDays(RETENTION_DAYS));
        int deletedRows = switch (definition.key()) {
            case "activity" -> activityRetentionCleanup.cleanupExpiredActivityData(now);
            case "attendance" -> cleanupAttendance(cutoff);
            case "stock-counts" -> cleanupStockCounts(cutoff);
            case "receiving" -> cleanupReceiving(cutoff);
            case "tasks" -> cleanupTasks(cutoff);
            case "reports" -> cleanupReports(cutoff);
            case "video-analytics" -> jdbcTemplate.update(
                    "delete from knowledge_sop_watch_sessions where last_heartbeat_at < ?",
                    Timestamp.from(cutoff)
            );
            case "sku-approvals" -> jdbcTemplate.update(
                    "delete from stock_sku_change_requests where workflow_status = 'DONE' and updated_at < ?",
                    Timestamp.from(cutoff)
            );
            case "unused-media" -> cleanupUnusedMedia(cutoff);
            default -> throw new IllegalStateException(
                    "Unhandled cleanup category " + definition.key()
            );
        };
        return new StorageCleanupResponse(definition.key(), deletedRows, now);
    }

    private List<StorageOverviewResponse.TableUsage> loadTableUsage() {
        return jdbcTemplate.query(
                """
                select tables.relname as table_name,
                       greatest(coalesce(stats.n_live_tup, 0), 0) as estimated_rows,
                       pg_table_size(tables.oid) as data_bytes,
                       pg_indexes_size(tables.oid) as index_bytes,
                       pg_total_relation_size(tables.oid) as total_bytes
                from pg_class tables
                join pg_namespace namespace on namespace.oid = tables.relnamespace
                left join pg_stat_user_tables stats on stats.relid = tables.oid
                where namespace.nspname = 'public'
                  and tables.relkind in ('r', 'p')
                order by total_bytes desc, table_name
                """,
                (resultSet, rowNumber) -> {
                    String tableName = resultSet.getString("table_name");
                    return new StorageOverviewResponse.TableUsage(
                            tableName,
                            tableGroup(tableName),
                            tableDataUse(tableName),
                            resultSet.getLong("estimated_rows"),
                            resultSet.getLong("data_bytes"),
                            resultSet.getLong("index_bytes"),
                            resultSet.getLong("total_bytes")
                    );
                }
        );
    }

    private int cleanupAttendance(Instant cutoff) {
        int deleted = jdbcTemplate.update(
                "delete from attendance_events where occurred_at < ?",
                Timestamp.from(cutoff)
        );
        deleted += jdbcTemplate.update(
                """
                delete from attendance_qr_codes code
                where code.created_at < ?
                  and not exists (
                        select 1 from attendance_events attendance
                        where attendance.qr_code_id = code.id
                  )
                """,
                Timestamp.from(cutoff)
        );
        return deleted;
    }

    private int cleanupStockCounts(Instant cutoff) {
        int deleted = jdbcTemplate.update(
                "delete from stock_count_submissions where captured_at < ?",
                Timestamp.from(cutoff)
        );
        return deleted + cleanupUnusedMedia(cutoff);
    }

    private int cleanupReceiving(Instant cutoff) {
        int deleted = jdbcTemplate.update(
                "delete from stock_receivings where captured_at < ?",
                Timestamp.from(cutoff)
        );
        return deleted + cleanupUnusedMedia(cutoff);
    }

    private int cleanupTasks(Instant cutoff) {
        LocalDate cutoffDate = cutoff.atZone(ZoneOffset.UTC).toLocalDate();
        int deleted = jdbcTemplate.update(
                "delete from task_records where task_date < ?",
                Date.valueOf(cutoffDate)
        );
        return deleted + cleanupUnusedMedia(cutoff);
    }

    private int cleanupReports(Instant cutoff) {
        LocalDate cutoffDate = cutoff.atZone(ZoneOffset.UTC).toLocalDate();
        int deleted = jdbcTemplate.update(
                "delete from business_reports where report_date < ?",
                Date.valueOf(cutoffDate)
        );
        return deleted + cleanupUnusedMedia(cutoff);
    }

    private int cleanupUnusedMedia(Instant cutoff) {
        int deleted = jdbcTemplate.update(
                """
                delete from stock_media media
                where media.created_at < ?
                  and not exists (
                        select 1 from stock_skus sku
                        where sku.tenant_id = media.tenant_id
                          and sku.thumbnail_media_id = media.id
                  )
                  and not exists (
                        select 1 from stock_count_submissions count_record
                        where count_record.tenant_id = media.tenant_id
                          and (count_record.stock_photo_name = media.storage_key
                               or count_record.invoice_photo_name = media.storage_key)
                  )
                  and not exists (
                        select 1 from stock_receivings receiving
                        where receiving.tenant_id = media.tenant_id
                          and (receiving.invoice_photo_name = media.storage_key
                               or receiving.goods_photo_name = media.storage_key)
                  )
                """,
                Timestamp.from(cutoff)
        );
        deleted += jdbcTemplate.update(
                """
                delete from report_media media
                where media.created_at < ?
                  and not exists (
                        select 1 from advertisements advertisement
                        where advertisement.tenant_id = media.tenant_id
                          and advertisement.image_storage_key = media.storage_key
                  )
                  and not exists (
                        select 1 from sales_void_bills bill
                        where bill.tenant_id = media.tenant_id
                          and bill.photo_media_id = media.id
                  )
                  and not exists (
                        select 1 from waste_report_details waste
                        where waste.tenant_id = media.tenant_id
                          and waste.photo_media_id = media.id
                  )
                  and not exists (
                        select 1 from daily_report_photos photo
                        where photo.tenant_id = media.tenant_id
                          and photo.photo_media_id = media.id
                  )
                  and not exists (
                        select 1 from complaint_report_details complaint
                        where complaint.tenant_id = media.tenant_id
                          and complaint.photo_media_id = media.id
                  )
                  and not exists (
                        select 1 from task_photos task_photo
                        where task_photo.tenant_id = media.tenant_id
                          and task_photo.photo_media_id = media.id
                  )
                """,
                Timestamp.from(cutoff)
        );
        return deleted;
    }

    private void assertStorageAdmin(AuthenticatedUser principal) {
        UserAccount actor = userAccountRepository
                .findByIdAndTenant_Id(principal.userId(), principal.tenantId())
                .orElseThrow(() -> forbidden());
        UserAccount founder = userAccountRepository
                .findFirstByTenant_IdOrderByCreatedAtAscIdAsc(principal.tenantId())
                .orElseThrow(() -> forbidden());
        var initialIdentity = loginIdentityRepository
                .findFirstByOrderByCreatedAtAscIdAsc()
                .orElseThrow(() -> forbidden());
        var initialTenant = tenantRepository
                .findFirstByOrderByCreatedAtAscIdAsc()
                .orElseThrow(() -> forbidden());
        if (!principal.isOwner()
                || !actor.getId().equals(founder.getId())
                || !actor.getIdentity().getId().equals(initialIdentity.getId())
                || !actor.getTenant().getId().equals(initialTenant.getId())
                || !ADMIN_EMPLOYEE_ID.equals(actor.getEmployeeId())
                || !ADMIN_PHONE.equals(actor.getPhoneE164())) {
            throw forbidden();
        }
    }

    private static ApiException forbidden() {
        return new ApiException(
                HttpStatus.FORBIDDEN,
                "STORAGE_ADMIN_REQUIRED",
                "Storage management is restricted to the founding administrator."
        );
    }

    private static String tableGroup(String tableName) {
        if (tableName.startsWith("attendance_")) return "Attendance";
        if (tableName.startsWith("stock_")) return "Stock";
        if (tableName.startsWith("task_")) return "Tasks";
        if (tableName.startsWith("knowledge_")) return "Knowledge";
        if (tableName.startsWith("report_")
                || tableName.startsWith("business_report")
                || tableName.startsWith("sales_")
                || tableName.startsWith("waste_")
                || tableName.startsWith("daily_report")
                || tableName.startsWith("complaint_")) return "Reports";
        if (tableName.startsWith("activity_")
                || tableName.startsWith("user_notification")
                || tableName.startsWith("push_")) return "Activity";
        if (tableName.startsWith("translation_")) return "Translation";
        if (tableName.startsWith("user_point")) return "Points";
        if (Set.of("tenants", "roles", "users", "login_identities").contains(tableName)) {
            return "Organisation";
        }
        return "System";
    }

    private static String tableDataUse(String tableName) {
        return switch (tableName) {
            case "application_setup" -> "One-time application setup state";
            case "tenants" -> "Company records and work locations";
            case "roles" -> "Company roles";
            case "login_identities" -> "Login profile, phone and password hash";
            case "users" -> "Employee membership in a company";
            case "user_sessions" -> "Login sessions";
            case "activity_events" -> "Workflow activity history";
            case "user_notifications" -> "Per-user notification state";
            case "push_devices" -> "Registered notification devices";
            case "push_outbox" -> "Pending and sent push deliveries";
            case "attendance_qr_codes" -> "Attendance QR tokens and expiry";
            case "attendance_events" -> "Check-in/out, device and location records";
            case "stock_media" -> "SKU, count and receiving image bytes";
            case "stock_tags" -> "Stock categories";
            case "stock_suppliers" -> "Supplier setup and purchase state";
            case "stock_skus" -> "SKU setup and current balances";
            case "stock_sku_change_requests" -> "SKU approval requests";
            case "stock_sku_suppliers" -> "SKU-to-supplier links";
            case "stock_sku_assignees" -> "SKU assignee names";
            case "stock_sku_receiving_checklist" -> "SKU receiving checklist templates";
            case "stock_count_submissions" -> "Daily stock count history";
            case "stock_count_submission_checks" -> "Daily count checklist results";
            case "stock_count_submission_remarks" -> "Daily count remarks";
            case "stock_receivings" -> "Receiving history and review status";
            case "stock_receiving_items" -> "SKU quantities in each receiving";
            case "knowledge_sops" -> "SOP content";
            case "knowledge_sop_watch_sessions" -> "SOP video viewing analytics";
            case "translation_cache" -> "Reusable translated content";
            case "user_point_adjustments" -> "Point score ledger; retained because it calculates current scores";
            case "report_media" -> "Report, task and advertisement image bytes";
            case "advertisements" -> "Home advertisements";
            case "business_reports" -> "Report workflow and dates";
            case "sales_report_details" -> "Sales report values";
            case "sales_void_bills" -> "Void bill details and photo links";
            case "waste_report_details" -> "Waste report details and photo link";
            case "daily_report_photos" -> "Daily report photo links";
            case "complaint_report_details" -> "Complaint details and photo link";
            case "stock_tag_assignees" -> "Users assigned to stock tags";
            case "task_templates" -> "Reusable task schedules";
            case "task_template_checklist_items" -> "Task checklist templates";
            case "task_records" -> "Scheduled task history and status";
            case "task_record_checklist_items" -> "Task checklist results";
            case "task_photos" -> "Task photo links";
            case "flyway_schema_history" -> "Database migration history";
            default -> "Application data";
        };
    }

    private record CleanupDefinition(
            String key,
            String title,
            String description,
            Set<String> tables,
            boolean automatic
    ) {
    }
}
