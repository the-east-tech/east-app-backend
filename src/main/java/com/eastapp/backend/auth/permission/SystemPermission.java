package com.eastapp.backend.auth.permission;

public enum SystemPermission {
    REPORT_INTELLIGENCE_VIEW,
    REPORT_OPERATIONS_ACCESS,
    SALES_REPORT_ACCESS,
    REPORT_REVIEW,
    KNOWLEDGE_AUDIT_VIEW,
    TASK_VIEW,
    TASK_CONTRIBUTE,
    TASK_VIEW_ALL,
    TASK_MANAGE,
    TASK_RATE;

    public String authority() {
        return "PERMISSION_" + name();
    }
}
