package com.eastapp.backend.auth.permission;

public enum SystemPermission {
    REPORT_INTELLIGENCE_VIEW,
    REPORT_OPERATIONS_ACCESS,
    SALES_REPORT_ACCESS,
    REPORT_REVIEW,
    KNOWLEDGE_AUDIT_VIEW,
    DAILY_TASK_VIEW,
    DAILY_TASK_CONTRIBUTE,
    DAILY_TASK_VIEW_ALL,
    DAILY_TASK_MANAGE,
    DAILY_TASK_RATE;

    public String authority() {
        return "PERMISSION_" + name();
    }
}
