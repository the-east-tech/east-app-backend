package com.eastapp.backend.common.deletion;

import com.eastapp.backend.common.api.DeletionDependencyResponse;
import com.eastapp.backend.common.api.DeletionPreviewResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class DeletionPreviewService {

    private final JdbcTemplate jdbcTemplate;

    public DeletionPreviewService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DeletionPreviewResponse user(UUID tenantId, UUID userId) {
        List<DeletionDependencyResponse> items = new ArrayList<>();
        addByUser(items, tenantId, userId, "ACTIVITY", "Activity history",
                "Settings > Business Backup & Cleanup", "activity_events", "actor_user_id");
        addByUser(items, tenantId, userId, "ATTENDANCE_QR", "Attendance QR codes",
                "People > Attendance", "attendance_qr_codes", "generated_by_user_id");
        addByUser(items, tenantId, userId, "ATTENDANCE", "Attendance records",
                "People > Attendance", "attendance_events", "user_id");
        addByUser(items, tenantId, userId, "TAGS", "Stock Tags created or updated",
                "Stock > Setup > Tag", "stock_tags", "created_by_user_id", "updated_by_user_id");
        addByUser(items, tenantId, userId, "SUPPLIERS", "Suppliers created or updated",
                "Stock > Setup > Supplier", "stock_suppliers", "created_by_user_id", "last_balance_updated_by_user_id");
        addByUser(items, tenantId, userId, "SKUS", "SKUs created or updated",
                "Stock > Setup > SKU", "stock_skus", "created_by_user_id", "last_updated_by_user_id");
        addByUser(items, tenantId, userId, "SKU_REQUESTS", "SKU change requests",
                "Stock > Setup > SKU", "stock_sku_change_requests", "requested_by_user_id", "reviewed_by_user_id");
        addByUser(items, tenantId, userId, "SKU_CSV_REQUESTS", "SKU CSV requests",
                "Stock > Setup > SKU", "stock_sku_csv_requests", "requested_by_user_id", "reviewed_by_user_id");
        addByUser(items, tenantId, userId, "SKU_CSV_EXPORT", "Approved SKU CSV export",
                "Stock > Setup > SKU", "stock_sku_export_snapshots", "approved_by_user_id");
        addByUser(items, tenantId, userId, "STOCK_COUNTS", "Stock Count records",
                "Stock > Count", "stock_count_submissions", "submitted_by_user_id", "reviewed_by_user_id");
        addByUser(items, tenantId, userId, "RECEIVABLES", "Receivable records",
                "Stock > Receivable", "stock_receivables", "received_by_user_id", "reviewed_by_user_id");
        addByUser(items, tenantId, userId, "SOPS", "Knowledge SOPs",
                "Knowledge", "knowledge_sops", "created_by_user_id");
        addByUser(items, tenantId, userId, "SOP_HISTORY", "SOP watch history",
                "Knowledge > Video Analytics", "knowledge_sop_watch_sessions", "user_id");
        addByUser(items, tenantId, userId, "POINTS", "Point adjustments",
                "People > Points", "user_point_adjustments", "recipient_user_id", "adjusted_by_user_id");
        addByUser(items, tenantId, userId, "REPORT_MEDIA", "Report photos",
                "Report", "report_media", "uploaded_by_user_id");
        addByUser(items, tenantId, userId, "ADVERTISEMENTS", "Advertisements",
                "Home > Advertisements", "advertisements", "created_by_user_id");
        addByUser(items, tenantId, userId, "REPORTS", "Business reports",
                "Report", "business_reports", "submitted_by_user_id", "reviewed_by_user_id", "amended_by_user_id");
        addByUser(items, tenantId, userId, "SALES_RECEIVER", "Sales cash receiver records",
                "Report > Sales", "sales_report_details", "cash_received_by_user_id");
        addByUser(items, tenantId, userId, "VOID_BILLS", "Sales void bills",
                "Report > Sales", "sales_void_bills", "created_by_user_id");
        addByUser(items, tenantId, userId, "DAILY_PHOTOS", "Daily report photos",
                "Report > Daily Photo", "daily_report_photos", "created_by_user_id");
        addByUser(items, tenantId, userId, "TAG_ASSIGNMENTS", "Stock Tag assignments",
                "Stock > Setup > Tag", "stock_tag_assignees", "user_id", "assigned_by_user_id");
        addByUser(items, tenantId, userId, "TASK_TEMPLATES", "Task templates",
                "Task", "task_templates", "created_by_user_id", "updated_by_user_id");
        addByUser(items, tenantId, userId, "TASK_RECORDS", "Task records",
                "Task", "task_records", "submitted_by_user_id", "rated_by_user_id");
        addByUser(items, tenantId, userId, "TASK_CHECKLIST", "Task checklist completions",
                "Task", "task_record_checklist_items", "completed_by_user_id");
        addByUser(items, tenantId, userId, "TASK_PHOTOS", "Task photos",
                "Task", "task_photos", "submitted_by_user_id");
        addByUser(items, tenantId, userId, "CLEANUP_HISTORY", "Backup and cleanup history",
                "Settings > Business Backup & Cleanup", "business_cleanup_runs", "requested_by_user_id");
        return response(items);
    }

    public DeletionPreviewResponse stockTag(UUID tenantId, UUID tagId) {
        List<DeletionDependencyResponse> items = new ArrayList<>();
        add(items, "SKUS", "Assigned SKUs", "Stock > Setup > SKU",
                "select count(*) from stock_skus where tenant_id = ? and (tag1_id = ? or tag2_id = ?)",
                tenantId, tagId, tagId);
        add(items, "SOPS", "Knowledge SOPs", "Knowledge",
                "select count(*) from knowledge_sops where tenant_id = ? and tag_id = ?", tenantId, tagId);
        add(items, "TAG_ASSIGNMENTS", "Assigned users", "Stock > Setup > Tag",
                "select count(*) from stock_tag_assignees where tenant_id = ? and tag_id = ?", tenantId, tagId);
        add(items, "TASK_TEMPLATES", "Task templates", "Task",
                "select count(*) from task_templates where tenant_id = ? and tag_id = ?", tenantId, tagId);
        add(items, "TASK_RECORDS", "Task records", "Task",
                "select count(*) from task_records where tenant_id = ? and tag_id = ?", tenantId, tagId);
        return response(items);
    }

    public DeletionPreviewResponse supplier(UUID tenantId, UUID supplierId) {
        List<DeletionDependencyResponse> items = new ArrayList<>();
        add(items, "SKUS", "Assigned SKUs", "Stock > Setup > SKU",
                """
                select count(*) from stock_sku_suppliers link
                join stock_skus sku on sku.id = link.sku_id
                where sku.tenant_id = ? and link.supplier_id = ?
                """, tenantId, supplierId);
        add(items, "RECEIVABLES", "Receivable records", "Stock > Receivable",
                "select count(*) from stock_receivables where tenant_id = ? and supplier_id = ?", tenantId, supplierId);
        return response(items);
    }

    public DeletionPreviewResponse sku(UUID tenantId, UUID skuId) {
        List<DeletionDependencyResponse> items = new ArrayList<>();
        add(items, "SUPPLIERS", "Assigned suppliers", "Stock > Setup > SKU",
                "select count(*) from stock_sku_suppliers where sku_id = ?", skuId);
        add(items, "ASSIGNEES", "Assigned staff names", "Stock > Setup > SKU",
                "select count(*) from stock_sku_assignees where sku_id = ?", skuId);
        add(items, "RECEIVABLE_CHECKLIST", "Receivable checklist items", "Stock > Setup > SKU",
                "select count(*) from stock_sku_receivable_checklist where sku_id = ?", skuId);
        add(items, "STOCK_COUNTS", "Stock Count records", "Stock > Count",
                "select count(*) from stock_count_submissions where tenant_id = ? and sku_id = ?", tenantId, skuId);
        add(items, "RECEIVABLES", "Receivable items", "Stock > Receivable",
                """
                select count(*) from stock_receivable_items item
                join stock_receivables receipt on receipt.id = item.receivable_id
                where receipt.tenant_id = ? and item.sku_id = ?
                """, tenantId, skuId);
        add(items, "WASTE_REPORTS", "Waste reports", "Report > Waste",
                "select count(*) from waste_report_details where tenant_id = ? and sku_id = ?", tenantId, skuId);
        return response(items);
    }

    public DeletionPreviewResponse sops(UUID tenantId, List<UUID> sopIds) {
        List<DeletionDependencyResponse> items = new ArrayList<>();
        if (sopIds.isEmpty()) return response(items);
        String placeholders = String.join(",", java.util.Collections.nCopies(sopIds.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenantId);
        parameters.addAll(sopIds);
        add(items, "WATCH_HISTORY", "SOP watch history", "Knowledge > Video Analytics",
                "select count(*) from knowledge_sop_watch_sessions where tenant_id = ? and sop_id in (" + placeholders + ")",
                parameters.toArray());
        add(items, "TASK_TEMPLATES", "Linked Task templates", "Task",
                "select count(*) from task_templates where tenant_id = ? and linked_sop_id in (" + placeholders + ")",
                parameters.toArray());
        add(items, "TASK_RECORDS", "Linked Task records", "Task",
                "select count(*) from task_records where tenant_id = ? and linked_sop_id in (" + placeholders + ")",
                parameters.toArray());
        return response(items);
    }

    private void addByUser(
            List<DeletionDependencyResponse> items,
            UUID tenantId,
            UUID userId,
            String code,
            String label,
            String location,
            String table,
            String... columns
    ) {
        String predicate = Arrays.stream(columns)
                .map(column -> column + " = ?")
                .reduce((left, right) -> left + " or " + right)
                .orElseThrow();
        Object[] parameters = new Object[columns.length + 1];
        parameters[0] = tenantId;
        Arrays.fill(parameters, 1, parameters.length, userId);
        add(items, code, label, location,
                "select count(*) from " + table + " where tenant_id = ? and (" + predicate + ")",
                parameters);
    }

    private void add(
            List<DeletionDependencyResponse> items,
            String code,
            String label,
            String location,
            String sql,
            Object... parameters
    ) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        if (count != null && count > 0) {
            items.add(new DeletionDependencyResponse(code, label, count, location));
        }
    }

    private static DeletionPreviewResponse response(List<DeletionDependencyResponse> items) {
        List<DeletionDependencyResponse> dependencies = List.copyOf(items);
        return new DeletionPreviewResponse(dependencies.isEmpty(), dependencies);
    }
}
