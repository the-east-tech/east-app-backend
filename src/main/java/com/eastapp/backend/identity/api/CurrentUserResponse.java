package com.eastapp.backend.identity.api;

import com.eastapp.backend.identity.UserAccount;

public record CurrentUserResponse(
        TenantResponse tenant,
        UserResponse user
) {
    public static CurrentUserResponse from(UserAccount user) {
        return new CurrentUserResponse(
                TenantResponse.from(user.getTenant()),
                UserResponse.from(user)
        );
    }
}
