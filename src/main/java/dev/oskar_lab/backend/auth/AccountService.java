package dev.oskar_lab.backend.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Owns registration, provider linking, onboarding, and revocable platform sessions. */
@Service
public class AccountService {
    public static final String TERMS_VERSION = "2026-09-21";
    private final AccountRepository accounts;
    private final LoginSessionRepository sessions;
    private final String dummyHash = Passwords.hash("unused-timing-equalization-password");

    public AccountService(AccountRepository accounts, LoginSessionRepository sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    /** Creates a local account only after all registration requirements pass. */
    @Transactional
    public Map<String, Object> register(Map<String, Object> input) {
        String username = username(input);
        String email = text(input, "email").strip().toLowerCase(Locale.ROOT);
        String password = text(input, "password");
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) fail(400, "invalidEmail");
        if (password.length() < 12 || password.length() > 128) fail(400, "invalidPassword");
        if (!password.equals(text(input, "passwordRepeat"))) fail(400, "passwordMismatch");
        requireTerms(input);
        if (accounts.findByEmail(email).isPresent() || accounts.findByUsernameKey(username.toLowerCase(Locale.ROOT)).isPresent()) fail(409, "accountExists");
        Account account = new Account();
        account.email = email;
        account.username = username;
        account.usernameKey = username.toLowerCase(Locale.ROOT);
        account.passwordHash = Passwords.hash(password);
        acceptTerms(account);
        accounts.saveAndFlush(account);
        return identity(account);
    }

    /** Accepts either a case-insensitive username or email as the login identifier. */
    @Transactional
    public Map<String, Object> login(Map<String, Object> input) {
        String identifier = text(input, "identifier").strip().toLowerCase(Locale.ROOT);
        String password = text(input, "password");
        if (identifier.length() > 254 || password.length() > 128) fail(401, "invalidCredentials");
        Account account = (identifier.contains("@") ? accounts.findByEmail(identifier) : accounts.findByUsernameKey(identifier)).orElse(null);
        boolean valid = Passwords.matches(password, account == null || account.passwordHash == null ? dummyHash : account.passwordHash);
        if (!valid || account == null || account.passwordHash == null) fail(401, "invalidCredentials");
        return createSession(account);
    }

    /** Only the trusted OAuth server may submit a Google identity after token verification. */
    @Transactional
    public Map<String, Object> google(Map<String, Object> input) {
        String subject = text(input, "subject");
        String email = text(input, "email").strip().toLowerCase(Locale.ROOT);
        if (subject.isBlank() || subject.length() > 255 || email.length() > 254 || !email.contains("@") || !Boolean.TRUE.equals(input.get("emailVerified"))) fail(403, "unverifiedGoogle");
        Account account = accounts.findByGoogleSubject(subject).orElse(null);
        if (account != null) return createSession(account);
        account = accounts.findByEmail(email).orElse(null);
        if (account != null) return Map.of("linkRequired", true);
        account = new Account();
        account.email = email;
        account.googleSubject = subject;
        accounts.saveAndFlush(account);
        return createSession(account);
    }

    /** Requires the existing password to prevent email-based account pre-hijacking. */
    @Transactional
    public Map<String, Object> linkGoogle(Map<String, Object> input) {
        String email = text(input, "email").strip().toLowerCase(Locale.ROOT);
        String subject = text(input, "subject");
        String password = text(input, "password");
        Account account = accounts.findByEmail(email).orElse(null);
        if (password.length() > 128 || subject.isBlank() || subject.length() > 255) fail(401, "invalidCredentials");
        boolean valid = Passwords.matches(password, account == null || account.passwordHash == null ? dummyHash : account.passwordHash);
        if (!valid || account == null || account.passwordHash == null) fail(401, "invalidCredentials");
        if (account.googleSubject != null && !account.googleSubject.equals(subject)) fail(409, "accountExists");
        account.googleSubject = subject;
        accounts.saveAndFlush(account);
        return createSession(account);
    }

    /** Completes Google onboarding; partial identities cannot access protected features. */
    @Transactional
    public Map<String, Object> complete(String token, Map<String, Object> input) {
        Account account = sessionAccount(token);
        String username = username(input);
        requireTerms(input);
        if (account.username != null) return identity(account);
        if (accounts.findByUsernameKey(username.toLowerCase(Locale.ROOT)).isPresent()) fail(409, "usernameTaken");
        account.username = username;
        account.usernameKey = username.toLowerCase(Locale.ROOT);
        acceptTerms(account);
        accounts.saveAndFlush(account);
        return identity(account);
    }

    /** Returns the current account only for an unexpired, unrevoked session. */
    @Transactional(readOnly = true)
    public Map<String, Object> current(String token) { return identity(sessionAccount(token)); }

    /** Immediately revokes the supplied session. */
    @Transactional
    public void logout(String token) { sessions.deleteById(digest(token)); }

    private Account sessionAccount(String token) {
        LoginSession session = sessions.findById(digest(token)).orElseThrow(() -> new AuthFailure(401, "sessionExpired"));
        if (!session.expiresAt.isAfter(Instant.now())) fail(401, "sessionExpired");
        return accounts.findById(session.accountId).orElseThrow(() -> new AuthFailure(401, "sessionExpired"));
    }

    private Map<String, Object> createSession(Account account) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LoginSession session = new LoginSession();
        session.digest = digest(token);
        session.accountId = account.id;
        session.expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        sessions.save(session);
        return Map.of("user", identity(account), "token", token);
    }

    private Map<String, Object> identity(Account account) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", account.id.toString());
        result.put("name", account.username);
        result.put("email", account.email);
        result.put("complete", account.username != null && account.termsAcceptedAt != null);
        result.put("termsVersion", account.termsVersion);
        return result;
    }

    private static String username(Map<String, Object> input) {
        String value = text(input, "username").strip();
        if (!value.matches("[a-zA-Z0-9_]{3,32}")) fail(400, "invalidUsername");
        return value;
    }

    private static void requireTerms(Map<String, Object> input) {
        if (!Boolean.TRUE.equals(input.get("acceptTerms")) || !TERMS_VERSION.equals(text(input, "termsVersion"))) fail(400, "termsRequired");
    }

    private static void acceptTerms(Account account) { account.termsVersion = TERMS_VERSION; account.termsAcceptedAt = Instant.now(); }
    private static String text(Map<String, Object> input, String key) { return input.get(key) instanceof String value ? value : ""; }
    private static void fail(int status, String code) { throw new AuthFailure(status, code); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
