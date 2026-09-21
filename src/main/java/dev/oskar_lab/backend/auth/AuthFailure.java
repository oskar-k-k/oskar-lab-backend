package dev.oskar_lab.backend.auth;

/** A safe machine-readable error that never includes submitted credentials. */
public class AuthFailure extends RuntimeException {
    public final int status;
    public AuthFailure(int status, String code) { super(code); this.status = status; }
}
