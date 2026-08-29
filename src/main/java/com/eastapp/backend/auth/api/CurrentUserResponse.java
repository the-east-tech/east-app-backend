package com.eastapp.backend.auth.api;

import com.eastapp.backend.auth.permission.RolePermissionPolicy;
import com.eastapp.backend.auth.permission.SystemPermission;
import com.eastapp.backend.organisation.api.BusinessResponse;

import com.eastapp.backend.people.api.UserResponse;
import com.eastapp.backend.people.UserAccount;

import java.util.List;

public record CurrentUserResponse(
        BusinessResponse tenant,
        UserResponse user,
        List<SystemPermission> permissions
) {
    public static CurrentUserResponse from(UserAccount user) {
        return new CurrentUserResponse(
                BusinessResponse.from(user.getTenant()),
                UserResponse.from(user),
                RolePermissionPolicy.grantedTo(user.getRole().getSystemKey()).stream()
                        .sorted()
                        .toList()
        );
    }
}
