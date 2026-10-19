package dev.oskar_lab.backend.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** Documents the JSON variants emitted by the existing account service. */
public final class AuthDtos {
    private AuthDtos() {}

    @Schema(name = "PlatformIdentity", requiredProperties = {"id", "name", "email", "complete", "termsVersion"})
    public record Identity(String id, @Schema(nullable = true) String name, String email,
                           boolean complete, @Schema(nullable = true) String termsVersion) {}

    @Schema(requiredProperties = {"user", "token"})
    public record Login(Identity user, String token) {}

    @Schema(requiredProperties = {"linkRequired"})
    public record LinkRequired(boolean linkRequired) {}

    @Schema(requiredProperties = {"ok"})
    public record Logout(boolean ok) {}
}
