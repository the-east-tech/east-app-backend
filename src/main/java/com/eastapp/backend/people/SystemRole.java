package com.eastapp.backend.people;

public enum SystemRole {
    ADMIN(0),
    OWNER(1),
    HEAD(2),
    MANAGER(3),
    SUPERVISOR(4),
    SENIOR_STAFF(5),
    STAFF(6),
    PART_TIME(7);

    private final int rank;

    SystemRole(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean canView(SystemRole target) {
        return target != null && target.rank >= rank;
    }

    public boolean canManage(SystemRole target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case ADMIN -> true;
            case OWNER -> target.rank >= OWNER.rank;
            case HEAD -> target.rank >= HEAD.rank;
            case MANAGER -> target.rank > MANAGER.rank;
            default -> false;
        };
    }

    public boolean canAssign(SystemRole target) {
        return canManage(target);
    }

    public boolean canAccessUserManagement() {
        return this == ADMIN || this == OWNER || this == HEAD || this == MANAGER;
    }
}
