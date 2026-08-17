package com.eastapp.backend.auth.api;

public record LoginResponse(
        String token,
        CurrentUserResponse currentUser
) {
}
