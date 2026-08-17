package com.eastapp.backend.people;

public enum SystemRole {
    OWNER(1),
    HEAD(2),
    MANAGER(3),
    SUPERVISOR(4),
    STAFF_1(5),
    STAFF_2(6);

    private final int rank;

    SystemRole(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean canView(SystemRole target) {
        return target != null && (this == OWNER || target.rank >= rank);
    }

    public boolean canManage(SystemRole target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case OWNER -> true;
            case HEAD -> target.rank >= HEAD.rank;
            case MANAGER -> target.rank > MANAGER.rank;
            default -> false;
        };
    }

    public boolean canAssign(SystemRole target) {
        return canManage(target);
    }

    public boolean canAccessUserManagement() {
        return this == OWNER || this == HEAD || this == MANAGER;
    }
}
