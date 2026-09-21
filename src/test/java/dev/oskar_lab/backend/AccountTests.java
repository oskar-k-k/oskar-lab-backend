package dev.oskar_lab.backend;

import dev.oskar_lab.backend.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AccountTests {
    @Autowired AccountService service;
    @Autowired AccountRepository repository;

    private Map<String, Object> registration(String name) {
        return Map.of("username", name, "email", name + "@example.com", "password", "long test password", "passwordRepeat", "long test password", "acceptTerms", true, "termsVersion", AccountService.TERMS_VERSION);
    }

    @Test void registersHashesAndRevokesSessions() {
        var user = service.register(registration("localuser"));
        var stored = repository.findByEmail("localuser@example.com").orElseThrow();
        assertNotEquals("long test password", stored.passwordHash);
        assertTrue(stored.passwordHash.startsWith("pbkdf2$600000$"));
        assertNotNull(stored.termsAcceptedAt);
        var login = service.login(Map.of("identifier", "LOCALUSER", "password", "long test password"));
        String token = (String) login.get("token");
        assertEquals(user.get("id"), service.current(token).get("id"));
        assertFalse(service.current(token).containsKey("passwordHash"));
        service.logout(token);
        assertThrows(AuthFailure.class, () -> service.current(token));
    }

    @Test void rejectsMissingConsentInvalidPasswordsAndDuplicates() {
        var data = new HashMap<>(registration("invaliduser"));
        data.put("acceptTerms", false);
        assertEquals("termsRequired", assertThrows(AuthFailure.class, () -> service.register(data)).getMessage());
        data.put("acceptTerms", true); data.put("passwordRepeat", "different password");
        assertEquals("passwordMismatch", assertThrows(AuthFailure.class, () -> service.register(data)).getMessage());
        service.register(registration("duplicateuser"));
        assertThrows(AuthFailure.class, () -> service.register(registration("duplicateuser")));
        assertThrows(AuthFailure.class, () -> service.login(Map.of("identifier", "duplicateuser", "password", "wrong")));
    }

    @Test void googleRequiresOnboardingAndKeepsStableIdentity() {
        var google = Map.<String, Object>of("subject", "google-new-user", "email", "google@example.com", "emailVerified", true);
        String token = (String) service.google(google).get("token");
        assertEquals(false, service.current(token).get("complete"));
        var completed = service.complete(token, Map.of("username", "googleuser", "acceptTerms", true, "termsVersion", AccountService.TERMS_VERSION));
        assertEquals(true, completed.get("complete"));
        String next = (String) service.google(google).get("token");
        assertEquals(completed.get("id"), service.current(next).get("id"));
    }

    @Test void requiresExistingPasswordBeforeLinkingGoogle() {
        var local = service.register(registration("linkeduser"));
        var google = Map.<String, Object>of("subject", "google-link-user", "email", "linkeduser@example.com", "emailVerified", true);
        assertEquals(true, service.google(google).get("linkRequired"));
        var linking = new HashMap<>(google); linking.put("password", "wrong password");
        assertThrows(AuthFailure.class, () -> service.linkGoogle(linking));
        linking.put("password", "long test password");
        String token = (String) service.linkGoogle(linking).get("token");
        assertEquals(local.get("id"), service.current(token).get("id"));
        String next = (String) service.google(google).get("token");
        assertEquals(local.get("id"), service.current(next).get("id"));
        assertNotNull(service.login(Map.of("identifier", "linkeduser@example.com", "password", "long test password")).get("token"));
    }

    @Test void rejectsUnverifiedGoogleEmail() {
        assertThrows(AuthFailure.class, () -> service.google(Map.of("subject", "unverified", "email", "user@example.com", "emailVerified", false)));
    }
}
