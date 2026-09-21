package dev.oskar_lab.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persists platform sessions for validation and logout revocation. */
public interface LoginSessionRepository extends JpaRepository<LoginSession, String> {}
