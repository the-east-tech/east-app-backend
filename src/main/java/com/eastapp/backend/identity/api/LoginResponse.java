package com.eastapp.backend.identity.api;

public record LoginResponse(
        String token,
        CurrentUserResponse currentUser
) {
}
