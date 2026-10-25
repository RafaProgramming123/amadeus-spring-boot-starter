package io.github.rafaprogramming123.amadeus.error;

import io.github.rafaprogramming123.amadeus.dto.common.Issue;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Amadeus error bodies come in two shapes:
 *  - API calls:      {"errors":[{"status":400,"code":477,"title":"INVALID FORMAT","detail":"..."}]}
 *  - token endpoint: {"error":"invalid_client","error_description":"...","code":38187,"title":"..."}
 * This record accepts both.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AmadeusErrorResponse(
        List<Issue> errors,
        String error,
        @JsonProperty("error_description") String errorDescription,
        Integer code,
        String title
) {
    /** Normalises both shapes into a list of issues. */
    public List<Issue> issues(int status) {
        if (errors != null && !errors.isEmpty()) {
            return errors;
        }
        if (error != null || title != null) {
            String detail = errorDescription != null ? errorDescription : error;
            return List.of(new Issue(status, code, title != null ? title : error, detail, null));
        }
        return List.of();
    }
}
