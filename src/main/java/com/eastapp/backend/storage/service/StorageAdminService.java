package com.eastapp.backend.storage.service;

import com.eastapp.backend.auth.permission.SystemPermission;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.storage.api.StorageCleanupResponse;
import com.eastapp.backend.storage.api.StorageOverviewResponse;
import com.eastapp.backend.storage.api.StorageTableDataResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StorageAdminService {
    private static final int MAX_VIEW_ROWS = 100;
    private static final List<String> VIEW_DATE_COLUMNS = List.of(
            "report_date",
            "task_date",
            "occurred_at",
            "captured_at",
            "submitted_at",
            "started_at",
            "created_at",
            "installed_on",
            "updated_at"
    );
    private static final Set<String> REDACTED_COLUMNS = Set.of(
            "setup_code",
            "password_hash",
            "token_hash",
            "secret_hash",
            "token"
    );
    private static final String UNUSED_STOCK_MEDIA = """
            not exists (
                select 1 from stock_skus sku
                where sku.tenant_id = candidate.tenant_id
                  and sku.thumbnail_media_id = candidate.id
            )
            and not exists (
                select 1 from stock_count_submissions count_record
                where count_record.tenant_id = candidate.tenant_id
                  and (count_record.stock_photo_name = candidate.storage_key
                       or count_record.invoice_photo_name = candidate.storage_key)
            )
            and not exists (
                select 1 from stock_receivings receiving
                where receiving.tenant_id = candidate.tenant_id
                  and (receiving.invoice_photo_name = candidate.storage_key
                       or receiving.goods_photo_name = candidate.storage_key)
            )
            and not exists (
                select 1 from stock_sku_change_requests request
                where request.tenant_id = candidate.tenant_id
                  and request.workflow_status <> 'DONE'
                  and request.payload_json::jsonb ->> 'photoPath' = candidate.storage_key
            )
            """;

    private static final String UNUSED_REPORT_MEDIA = """
            not exists (
                select 1 from advertisements advertisement
                where advertisement.tenant_id = candidate.tenant_id
                  and advertisement.image_storage_key = candidate.storage_key
            )
            and not exists (
                select 1 from sales_void_bills bill
                where bill.tenant_id = candidate.tenant_id
                  and bill.photo_media_id = candidate.id
            )
            and not exists (
                select 1 from waste_report_details waste
                where waste.tenant_id = candidate.tenant_id
                  and waste.photo_media_id = candidate.id
            )
            and not exists (
                select 1 from daily_report_photos photo
                where photo.tenant_id = candidate.tenant_id
                  and photo.photo_media_id = candidate.id
            )
            and not exists (
                select 1 from complaint_report_details complaint
                where complaint.tenant_id = candidate.tenant_id
                  and complaint.photo_media_id = candidate.id
            )
            and not exists (
                select 1 from task_photos task_photo
                where task_photo.tenant_id = candidate.tenant_id
                  and task_photo.photo_media_id = candidate.id
            )
            """;

    private static final Map<String, CleanupPolicy> CLEANUP_POLICIES = Map.ofEntries(
            Map.entry("user_sessions", new CleanupPolicy(
                    "Revoked login sessions not referenced by attendance records.",
                    "candidate.revoked_at is not null and not exists (select 1 from attendance_events attendance where attendance.user_session_id = candidate.id)",
                    "candidate.created_at, candidate.id", false
            )),
            Map.entry("activity_events", new CleanupPolicy(
                    "Oldest activity events without an unread notification. Related read notifications are removed first.",
                    "not exists (select 1 from user_notifications notification where notification.activity_event_id = candidate.id and notification.read_at is null and notification.dismissed_at is null)",
                    "candidate.occurred_at, candidate.id", false
            )),
            Map.entry("user_notifications", new CleanupPolicy(
                    "Oldest read or dismissed notifications. Related push delivery rows are removed automatically.",
                    "candidate.read_at is not null or candidate.dismissed_at is not null",
                    "candidate.created_at, candidate.id", false
            )),
            Map.entry("push_devices", new CleanupPolicy(
                    "Oldest inactive notification devices. Related push delivery rows are removed automatically.",
                    "not candidate.active", "candidate.created_at, candidate.id", false
            )),
            Map.entry("push_outbox", new CleanupPolicy(
                    "Oldest push deliveries that were sent or have expired.",
                    "candidate.sent_at is not null or candidate.expires_at < current_timestamp",
                    "candidate.created_at, candidate.id", false
            )),
            Map.entry("attendance_events", new CleanupPolicy(
                    "Oldest attendance history. QR codes remain until they are no longer referenced.",
                    "true", "candidate.occurred_at, candidate.id", true
            )),
            Map.entry("attendance_qr_codes", new CleanupPolicy(
                    "Oldest expired or revoked QR codes that are not referenced by attendance history.",
                    "(candidate.revoked_at is not null or candidate.expires_at < current_timestamp) and not exists (select 1 from attendance_events attendance where attendance.qr_code_id = candidate.id)",
                    "candidate.created_at, candidate.id", false
            )),
            Map.entry("stock_media", new CleanupPolicy(
                    "Oldest photos that are not referenced by an SKU, stock count, receiving or pending SKU request.",
                    UNUSED_STOCK_MEDIA, "candidate.created_at, candidate.id", false
            )),
            Map.entry("stock_sku_change_requests", new CleanupPolicy(
                    "Oldest completed SKU approval requests. Pending approval requests remain protected.",
                    "candidate.workflow_status = 'DONE'", "candidate.updated_at, candidate.id", true
            )),
            Map.entry("stock_count_submissions", new CleanupPolicy(
                    "Oldest completed stock counts. Their checklist and remark rows are removed automatically.",
                    "candidate.review_status = 'DONE'", "candidate.captured_at, candidate.id", true
            )),
            Map.entry("stock_receivings", new CleanupPolicy(
                    "Oldest completed receiving records. Their item rows are removed automatically.",
                    "candidate.review_status = 'DONE'", "candidate.captured_at, candidate.id", true
            )),
            Map.entry("knowledge_sop_watch_sessions", new CleanupPolicy(
                    "Oldest SOP video viewing sessions.",
                    "true", "candidate.started_at, candidate.id", false
            )),
            Map.entry("translation_cache", new CleanupPolicy(
                    "Oldest cached translations. Deleted text is translated and cached again when needed.",
                    "true", "candidate.created_at, candidate.id", false
            )),
            Map.entry("report_media", new CleanupPolicy(
                    "Oldest photos that are not referenced by a report, task or advertisement.",
                    UNUSED_REPORT_MEDIA, "candidate.created_at, candidate.id", false
            )),
            Map.entry("advertisements", new CleanupPolicy(
                    "Oldest inactive or expired advertisements. Their unused images can then be removed from report_media.",
                    "not candidate.active or candidate.ends_at < current_timestamp",
                    "candidate.created_at, candidate.id", true
            )),
            Map.entry("business_reports", new CleanupPolicy(
                    "Oldest completed reports. Their detail and photo-link rows are removed automatically.",
                    "candidate.workflow_status = 'DONE'",
                    "candidate.report_date, candidate.created_at, candidate.id", true
            )),
            Map.entry("task_records", new CleanupPolicy(
                    "Oldest completed tasks. Their checklist and photo-link rows are removed automatically.",
                    "candidate.status = 'DONE'",
                    "candidate.task_date, candidate.created_at, candidate.id", true
            ))
    );

    private final JdbcTemplate jdbcTemplate;

    public StorageAdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
        return new StorageOverviewResponse(
                Instant.now(),
                databaseBytes,
                applicationBytes,
                MAX_VIEW_ROWS,
                tables
        );
    }

    @Transactional(readOnly = true)
    public StorageTableDataResponse tableData(
            AuthenticatedUser principal,
            String tableName,
            int requestedRows,
            boolean latestFirst
    ) {
        assertStorageAdmin(principal);
        if (requestedRows < 1 || requestedRows > MAX_VIEW_ROWS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TABLE_VIEW_ROW_COUNT_INVALID",
                    "Choose between 1 and " + MAX_VIEW_ROWS + " rows to view."
            );
        }

        List<TableColumnMetadata> tableColumns = loadTableColumns(tableName);
        if (tableColumns.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "STORAGE_TABLE_NOT_FOUND",
                    "The requested database table was not found."
            );
        }

        List<String> columnNames = tableColumns.stream()
                .map(TableColumnMetadata::name)
                .toList();
        String sql = "select " + viewProjection(tableColumns) + " from "
                + quoteIdentifier(tableName) + " candidate order by "
                + viewOrderBy(columnNames, latestFirst) + " limit ?";
        return jdbcTemplate.query(
                sql,
                statement -> statement.setInt(1, requestedRows),
                resultSet -> {
                    int columnCount = tableColumns.size();
                    List<StorageTableDataResponse.Column> columns = new ArrayList<>(columnCount);
                    for (TableColumnMetadata column : tableColumns) {
                        columns.add(new StorageTableDataResponse.Column(
                                column.name(),
                                column.dataType(),
                                column.nullable()
                        ));
                    }

                    List<List<String>> rows = new ArrayList<>();
                    while (resultSet.next()) {
                        List<String> row = new ArrayList<>(columnCount);
                        for (int index = 1; index <= columnCount; index++) {
                            row.add(displayValue(resultSet.getObject(index)));
                        }
                        rows.add(row);
                    }
                    return new StorageTableDataResponse(
                            tableName,
                            requestedRows,
                            rows.size(),
                            columns,
                            rows
                    );
                }
        );
    }

    @Transactional
    public StorageCleanupResponse cleanup(
            AuthenticatedUser principal,
            String tableName,
            boolean confirmed,
            int requestedRows
    ) {
        assertStorageAdmin(principal);
        if (!confirmed) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CLEANUP_CONFIRMATION_REQUIRED",
                    "Confirm the permanent deletion first."
            );
        }
        if (requestedRows < 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CLEANUP_ROW_COUNT_REQUIRED",
                    "Choose at least one row to delete."
            );
        }
        CleanupPolicy policy = CLEANUP_POLICIES.get(tableName);
        if (policy == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TABLE_CLEANUP_NOT_ALLOWED",
                    "This table does not support direct cleanup."
            );
        }

        int deletedRows;
        if (tableName.equals("activity_events")) {
            deletedRows = deleteActivityEvents(policy, requestedRows);
        } else {
            if (policy.deleteRelatedActivity()) {
                deleteRelatedActivity(tableName, policy, requestedRows);
            }
            deletedRows = deleteOldest(tableName, policy, requestedRows);
        }
        return new StorageCleanupResponse(tableName, deletedRows, Instant.now());
    }

    private List<StorageOverviewResponse.TableUsage> loadTableUsage() {
        List<TableMetadata> metadata = jdbcTemplate.query(
                """
                select tables.relname as table_name,
                       pg_table_size(tables.oid) as data_bytes,
                       pg_indexes_size(tables.oid) as index_bytes,
                       pg_total_relation_size(tables.oid) as total_bytes,
                       (
                           select columns.column_name
                           from information_schema.columns columns
                           where columns.table_schema = 'public'
                             and columns.table_name = tables.relname
                             and columns.data_type in (
                                 'date',
                                 'timestamp with time zone',
                                 'timestamp without time zone'
                             )
                           order by case columns.column_name
                               when 'report_date' then 1
                               when 'task_date' then 2
                               when 'occurred_at' then 3
                               when 'captured_at' then 4
                               when 'submitted_at' then 5
                               when 'started_at' then 6
                               when 'created_at' then 7
                               when 'installed_on' then 8
                               when 'updated_at' then 9
                               else 20
                           end,
                           columns.ordinal_position
                           limit 1
                       ) as date_column
                from pg_class tables
                join pg_namespace namespace on namespace.oid = tables.relnamespace
                where namespace.nspname = 'public'
                  and tables.relkind in ('r', 'p')
                order by total_bytes desc, table_name
                """,
                (resultSet, rowNumber) -> new TableMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("date_column"),
                        resultSet.getLong("data_bytes"),
                        resultSet.getLong("index_bytes"),
                        resultSet.getLong("total_bytes")
                )
        );
        return metadata.stream().map(this::loadExactTableUsage).toList();
    }

    private StorageOverviewResponse.TableUsage loadExactTableUsage(TableMetadata table) {
        String quotedTable = quoteIdentifier(table.tableName());
        String dates = table.dateColumn() == null
                ? "null::date as oldest_date, null::date as latest_date"
                : "min(" + quoteIdentifier(table.dateColumn()) + ")::date as oldest_date, "
                + "max(" + quoteIdentifier(table.dateColumn()) + ")::date as latest_date";
        ExactTableUsage exact = jdbcTemplate.queryForObject(
                "select count(*) as row_count, " + dates + " from " + quotedTable,
                (resultSet, rowNumber) -> new ExactTableUsage(
                        resultSet.getLong("row_count"),
                        resultSet.getObject("oldest_date", LocalDate.class),
                        resultSet.getObject("latest_date", LocalDate.class)
                )
        );
        CleanupPolicy policy = CLEANUP_POLICIES.get(table.tableName());
        boolean deleteAllowed = policy != null;
        long deletableRows = deleteAllowed ? loadDeletableRows(table.tableName(), policy) : 0;
        return new StorageOverviewResponse.TableUsage(
                table.tableName(),
                tableGroup(table.tableName()),
                tableDataUse(table.tableName()),
                exact.rowCount(),
                exact.oldestDate(),
                exact.latestDate(),
                table.dataBytes(),
                table.indexBytes(),
                table.totalBytes(),
                deleteAllowed,
                deletableRows,
                policy == null ? null : policy.description()
        );
    }

    private long loadDeletableRows(String tableName, CleanupPolicy policy) {
        String sql = "select count(*) from " + quoteIdentifier(tableName)
                + " candidate where " + policy.eligibility();
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private List<TableColumnMetadata> loadTableColumns(String tableName) {
        return jdbcTemplate.query(
                """
                select attribute.attname as column_name,
                       pg_catalog.format_type(attribute.atttypid, attribute.atttypmod) as data_type,
                       not attribute.attnotnull as nullable,
                       column_type.typname as type_name
                from pg_catalog.pg_attribute attribute
                join pg_catalog.pg_class table_info on table_info.oid = attribute.attrelid
                join pg_catalog.pg_namespace namespace on namespace.oid = table_info.relnamespace
                join pg_catalog.pg_type column_type on column_type.oid = attribute.atttypid
                where namespace.nspname = 'public'
                  and table_info.relname = ?
                  and table_info.relkind in ('r', 'p')
                  and attribute.attnum > 0
                  and not attribute.attisdropped
                order by attribute.attnum
                """,
                (resultSet, rowNumber) -> new TableColumnMetadata(
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getBoolean("nullable"),
                        resultSet.getString("type_name")
                ),
                tableName
        );
    }

    private static String viewProjection(List<TableColumnMetadata> columns) {
        return columns.stream()
                .map(column -> {
                    String quotedColumn = quoteIdentifier(column.name());
                    String value = "candidate." + quotedColumn;
                    if (REDACTED_COLUMNS.contains(column.name())) {
                        return "case when " + value + " is null then null "
                                + "else '[REDACTED]' end as " + quotedColumn;
                    }
                    if (column.typeName().equals("bytea")) {
                        return "case when " + value + " is null then null else '[BINARY ' || "
                                + "octet_length(" + value + ") || ' BYTES]' end as " + quotedColumn;
                    }
                    return value;
                })
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
    }

    private static String viewOrderBy(List<String> columnNames, boolean latestFirst) {
        String dateColumn = VIEW_DATE_COLUMNS.stream()
                .filter(columnNames::contains)
                .findFirst()
                .orElse(null);
        String firstOrderColumn = dateColumn == null ? columnNames.getFirst() : dateColumn;
        String direction = latestFirst ? " desc nulls last" : " asc nulls last";
        String orderBy = "candidate." + quoteIdentifier(firstOrderColumn) + direction;
        if (!firstOrderColumn.equals("id") && columnNames.contains("id")) {
            orderBy += ", candidate.\"id\"" + direction;
        }
        return orderBy;
    }

    private static String displayValue(Object value) {
        if (value == null) return null;
        return value.toString();
    }

    private int deleteActivityEvents(CleanupPolicy policy, int requestedRows) {
        String candidates = candidateIds("activity_events", policy);
        jdbcTemplate.update(
                "delete from user_notifications where activity_event_id in (" + candidates + ")",
                requestedRows
        );
        return jdbcTemplate.update(
                "delete from activity_events where id in (" + candidates + ")",
                requestedRows
        );
    }

    private void deleteRelatedActivity(
            String tableName,
            CleanupPolicy policy,
            int requestedRows
    ) {
        String candidates = candidateIds(tableName, policy);
        String events = "select event.id from activity_events event where event.target_id in ("
                + candidates + ")";
        jdbcTemplate.update(
                "delete from user_notifications where activity_event_id in (" + events + ")",
                requestedRows
        );
        jdbcTemplate.update(
                "delete from activity_events where target_id in (" + candidates + ")",
                requestedRows
        );
    }

    private int deleteOldest(
            String tableName,
            CleanupPolicy policy,
            int requestedRows
    ) {
        return jdbcTemplate.update(
                "delete from " + quoteIdentifier(tableName)
                        + " where id in (" + candidateIds(tableName, policy) + ")",
                requestedRows
        );
    }

    private String candidateIds(String tableName, CleanupPolicy policy) {
        return "select candidate.id from " + quoteIdentifier(tableName) + " candidate where "
                + policy.eligibility() + " order by " + policy.orderBy() + " limit ?";
    }

    private static String quoteIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Unsafe database identifier");
        }
        return '"' + identifier + '"';
    }

    private void assertStorageAdmin(AuthenticatedUser principal) {
        if (!principal.hasPermission(SystemPermission.STORAGE_ADMIN)) {
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

    private record TableMetadata(
            String tableName,
            String dateColumn,
            long dataBytes,
            long indexBytes,
            long totalBytes
    ) {
    }

    private record ExactTableUsage(
            long rowCount,
            LocalDate oldestDate,
            LocalDate latestDate
    ) {
    }

    private record TableColumnMetadata(
            String name,
            String dataType,
            boolean nullable,
            String typeName
    ) {
    }

    private record CleanupPolicy(
            String description,
            String eligibility,
            String orderBy,
            boolean deleteRelatedActivity
    ) {
    }
}
