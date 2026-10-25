package io.github.rafaprogramming123.amadeus.error;

import io.github.rafaprogramming123.amadeus.dto.common.Issue;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown for any non-2xx answer from Amadeus (after retries are exhausted).
 * Carries the HTTP status and the typed Amadeus issues so callers can react,
 * e.g. show "no seats left" instead of a generic 500.
 */
public class AmadeusException extends RuntimeException {

    private final int status;
    private final List<Issue> issues;
    private final String method;
    private final URI uri;
    private final String rawBody;

    public AmadeusException(int status, List<Issue> issues, String method, URI uri, String rawBody) {
        super(buildMessage(status, issues, method, uri, rawBody));
        this.status = status;
        this.issues = List.copyOf(issues);
        this.method = method;
        this.uri = uri;
        this.rawBody = rawBody;
    }

    public int status() { return status; }
    public List<Issue> issues() { return issues; }
    public String method() { return method; }
    public URI uri() { return uri; }
    public String rawBody() { return rawBody; }

    /** Our credentials or token are wrong — not the caller's fault. */
    public boolean isAuthError() { return status == 401 || status == 403; }
    public boolean isRateLimited() { return status == 429; }
    public boolean isClientError() { return status >= 400 && status < 500; }
    public boolean isServerError() { return status >= 500; }

    /** True if any issue carries this Amadeus error code (e.g. 477 = INVALID FORMAT). */
    public boolean hasCode(int code) {
        return issues.stream().anyMatch(i -> i.code() != null && i.code() == code);
    }

    private static String buildMessage(int status, List<Issue> issues, String method, URI uri, String rawBody) {
        String details = issues.isEmpty()
                ? truncate(rawBody)
                : issues.stream()
                        .map(i -> "[" + i.code() + "] " + i.title() + (i.detail() != null ? ": " + i.detail() : ""))
                        .collect(Collectors.joining("; "));
        return "Amadeus " + method + " " + (uri != null ? uri.getPath() : "?") + " failed with " + status + " — " + details;
    }

    private static String truncate(String s) {
        if (s == null || s.isBlank()) return "(empty body)";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
