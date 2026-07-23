package com.opc.platform.userauth;

public record AuthenticatedUser(
        Long userId,
        String username,
        String email
) {
}
