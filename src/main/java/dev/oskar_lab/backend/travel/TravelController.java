package dev.oskar_lab.backend.travel;

import dev.oskar_lab.backend.access.ResourceAccessService;
import dev.oskar_lab.backend.auth.AuthFailure;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static dev.oskar_lab.backend.travel.TravelDtos.*;
import static dev.oskar_lab.backend.travel.TravelTypes.*;

/** Server-only travel API. App servers must derive X-User-Id from a verified platform session. */
@RestController
@RequestMapping("/internal/travel/trips")
public class TravelController {
    private final TravelService travel;
    private final TravelAttachmentService attachments;
    private final ResourceAccessService access;
    private final String secret;

    public TravelController(TravelService travel, TravelAttachmentService attachments, ResourceAccessService access,
                            @Value("${auth.bridge-secret:}") String secret) {
        this.travel = travel; this.attachments = attachments; this.access = access; this.secret = secret;
    }

    @ModelAttribute
    void authorize(@RequestHeader(value = "X-Auth-Bridge", defaultValue = "") String key,
                   @RequestHeader("X-User-Id") UUID user, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Robots-Tag", "noindex, noarchive, nosnippet");
        if (secret.length() < 32 || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
            throw new AuthFailure(403, "forbidden");
        access.requireAccount(user);
    }

    /** Lists the current user's owned and email-shared trips, in pages of twenty. */
    @GetMapping
    public TripPage list(@RequestHeader("X-User-Id") UUID user, @RequestParam(defaultValue = "0") int page) { return travel.list(user, page); }

    /** Creates a private trip owned by the authenticated user; version must be zero. */
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Trip create(@RequestHeader("X-User-Id") UUID user, @Valid @RequestBody SaveTrip input) { return travel.create(user, input); }

    /** Reads a trip only for its owner or a verified email grantee. */
    @GetMapping("/{tripId}")
    public Trip get(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId) { return travel.get(user, tripId); }

    /** Updates trip metadata using the last-read version. */
    @PutMapping("/{tripId}")
    public Trip update(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId, @Valid @RequestBody SaveTrip input) { return travel.update(user, tripId, input); }

    /** Deletes the trip and all its data; version is the last-read trip version. */
    @DeleteMapping("/{tripId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId, @RequestParam long version) { travel.delete(user, tripId, version); }

    /** Lists booking groups rather than flattening hotel endpoints into unrelated events. */
    @GetMapping("/{tripId}/entries")
    public EntryPage entries(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId, @RequestParam(defaultValue = "0") int page) { return travel.entries(user, tripId, page); }

    /** Creates a typed booking and its points atomically. */
    @PostMapping("/{tripId}/entries") @ResponseStatus(HttpStatus.CREATED)
    public Entry createEntry(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId, @Valid @RequestBody SaveEntry input) { return travel.createEntry(user, tripId, input); }

    /** Reads a booking inside its authorized parent trip. */
    @GetMapping("/{tripId}/entries/{entryId}")
    public Entry getEntry(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId, @PathVariable UUID entryId) { return travel.getEntry(user, tripId, entryId); }

    /** Updates a booking using its own version, including point-only changes. */
    @PutMapping("/{tripId}/entries/{entryId}")
    public Entry updateEntry(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId, @PathVariable UUID entryId,
                             @Valid @RequestBody SaveEntry input) { return travel.updateEntry(user, tripId, entryId, input); }

    /** Deletes a booking and attachments using the last-read entry version. */
    @DeleteMapping("/{tripId}/entries/{entryId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId, @PathVariable UUID entryId, @RequestParam long version) { travel.deleteEntry(user, tripId, entryId, version); }

    /** Only owners may inspect the private sharing list. */
    @GetMapping("/{tripId}/viewers")
    public List<Viewer> viewers(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId) {
        return access.viewers(user, TravelService.RESOURCE_TYPE, tripId).stream().map(v -> new Viewer(v.id, v.email)).toList();
    }

    /** Adds a read-only email grant; no invitation message is sent. */
    @PostMapping("/{tripId}/viewers") @ResponseStatus(HttpStatus.CREATED)
    public Viewer invite(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId, @Valid @RequestBody InviteViewer input) {
        var viewer = access.invite(user, TravelService.RESOURCE_TYPE, tripId, input.email());
        return new Viewer(viewer.id, viewer.email);
    }

    /** Revokes a grant without deleting the invited person's account or other trips. */
    @DeleteMapping("/{tripId}/viewers/{viewerId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId, @PathVariable UUID viewerId) { access.revoke(user, TravelService.RESOURCE_TYPE, tripId, viewerId); }

    /** Lists attachment metadata without loading private binary contents. */
    @GetMapping("/{tripId}/entries/{entryId}/attachments")
    public List<Attachment> attachments(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId, @PathVariable UUID entryId) { return attachments.list(user, tripId, entryId); }

    /** Uploads one PDF, PNG or JPEG up to five MiB, optionally linked to a point role. */
    @PostMapping(value = "/{tripId}/entries/{entryId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Attachment upload(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId, @PathVariable UUID entryId,
        @RequestParam(required = false) PointRole pointRole, @RequestPart("file") MultipartFile file) throws IOException {
        travel.requireEntry(user, tripId, entryId, true);
        if (file.getSize() > TravelAttachmentService.MAX_BYTES) throw new AuthFailure(413, "fileTooLarge");
        return attachments.upload(user, tripId, entryId, pointRole, file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }

    /** Downloads an authorized file as an attachment, with active content sandboxed and sniffing disabled. */
    @GetMapping("/{tripId}/entries/{entryId}/attachments/{attachmentId}")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = {
        @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/octet-stream", schema =
            @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary"))})
    public ResponseEntity<byte[]> download(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId,
        @PathVariable UUID entryId, @PathVariable UUID attachmentId) {
        var result = attachments.download(user, tripId, entryId, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.metadata().mediaType()))
            .header("Content-Disposition", ContentDisposition.attachment().filename(result.metadata().filename(), StandardCharsets.UTF_8).build().toString())
            .header("Content-Security-Policy", "sandbox").body(result.bytes());
    }

    /** Deletes private attachment metadata and contents together. */
    @DeleteMapping("/{tripId}/entries/{entryId}/attachments/{attachmentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId,
        @PathVariable UUID entryId, @PathVariable UUID attachmentId) { attachments.delete(user, tripId, entryId, attachmentId); }

    @ExceptionHandler(AuthFailure.class)
    ResponseEntity<Map<String, String>> failure(AuthFailure error) { return error(error.status, error.getMessage()); }

    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<Map<String, String>> conflict() { return error(409, "conflict"); }

    @ExceptionHandler({MethodArgumentNotValidException.class, org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class, org.springframework.web.bind.MissingRequestHeaderException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class, org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<Map<String, String>> invalid() { return error(400, "invalidTravel"); }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge() { return error(413, "fileTooLarge"); }

    private ResponseEntity<Map<String, String>> error(int status, String code) {
        return ResponseEntity.status(status).header("Cache-Control", "private, no-store").body(Map.of("code", code));
    }
}
