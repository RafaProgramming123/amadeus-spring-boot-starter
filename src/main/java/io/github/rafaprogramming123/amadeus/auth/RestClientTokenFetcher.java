package io.github.rafaprogramming123.amadeus.auth;

import io.github.rafaprogramming123.amadeus.autoconfigure.AmadeusProperties;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** OAuth2 client_credentials call to the Amadeus token endpoint. */
public class RestClientTokenFetcher implements TokenFetcher {

    private final RestClient authClient;   // plain client: NO auth interceptor (avoids recursion)
    private final AmadeusProperties props;

    public RestClientTokenFetcher(RestClient authClient, AmadeusProperties props) {
        this.authClient = authClient;
        this.props = props;
    }

    @Override
    public TokenResponse fetch() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());

        TokenResponse response = authClient.post()
                .uri(props.tokenPath())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()          // 4xx/5xx → AmadeusException via the status handler
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("Amadeus token endpoint returned no access_token");
        }
        return response;
    }
}
