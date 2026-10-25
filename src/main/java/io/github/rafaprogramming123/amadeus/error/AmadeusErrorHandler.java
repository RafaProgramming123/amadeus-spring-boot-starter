package io.github.rafaprogramming123.amadeus.error;

import io.github.rafaprogramming123.amadeus.dto.common.Issue;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RestClient status handler: turns every 4xx/5xx from Amadeus into an AmadeusException
 * with the parsed issues. Registered on both the API client and the token client.
 */
public class AmadeusErrorHandler implements RestClient.ResponseSpec.ErrorHandler {

    private final JsonMapper mapper;

    public AmadeusErrorHandler(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        byte[] bytes = response.getBody().readAllBytes();
        String raw = new String(bytes, StandardCharsets.UTF_8);

        List<Issue> issues;
        try {
            issues = bytes.length == 0
                    ? List.of()
                    : mapper.readValue(bytes, AmadeusErrorResponse.class).issues(status);
        } catch (RuntimeException notJson) {   // HTML gateway page, empty body, etc.
            issues = List.of();
        }

        throw new AmadeusException(status, issues, request.getMethod().name(), request.getURI(), raw);
    }
}
