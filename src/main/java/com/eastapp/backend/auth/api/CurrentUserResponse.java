package com.eastapp.backend.auth.api;

import com.eastapp.backend.organisation.api.BusinessResponse;

import com.eastapp.backend.people.api.UserResponse;
import com.eastapp.backend.people.UserAccount;

public record CurrentUserResponse(
        BusinessResponse tenant,
        UserResponse user
) {
    public static CurrentUserResponse from(UserAccount user) {
        return new CurrentUserResponse(
                BusinessResponse.from(user.getTenant()),
                UserResponse.from(user)
        );
    }
}
