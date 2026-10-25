package io.github.rafaprogramming123.amadeus.autoconfigure;

import io.github.rafaprogramming123.amadeus.Amadeus;
import io.github.rafaprogramming123.amadeus.api.FlightOffersApi;
import io.github.rafaprogramming123.amadeus.auth.AmadeusTokenManager;
import io.github.rafaprogramming123.amadeus.auth.RestClientTokenFetcher;
import io.github.rafaprogramming123.amadeus.error.AmadeusErrorHandler;
import io.github.rafaprogramming123.amadeus.error.AmadeusExceptionAdvice;
import io.github.rafaprogramming123.amadeus.http.AmadeusJson;
import io.github.rafaprogramming123.amadeus.http.AmadeusRequestInterceptor;
import io.github.rafaprogramming123.amadeus.http.RetryPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;

/**
 * Wires the Amadeus client into any Spring Boot app that has this jar on the classpath.
 *
 * Activates only when {@code amadeus.client-id} is set, so adding the dependency alone
 * never breaks an app. Every public bean is {@code @ConditionalOnMissingBean}: an app
 * can replace any piece by declaring its own bean.
 *
 * Internals (RestClient, token manager, proxy factory) are deliberately NOT beans, so
 * they never clash with the app's own RestClient or HttpServiceProxyFactory beans.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
        "org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration"
})
@ConditionalOnProperty(prefix = "amadeus", name = "client-id")
@EnableConfigurationProperties(AmadeusProperties.class)
public class AmadeusAutoConfiguration {

    /** Holder for the shared internals, so they're built once and stay private. */
    public record AmadeusClients(HttpServiceProxyFactory proxyFactory, AmadeusTokenManager tokenManager) {}

    @Bean
    @ConditionalOnMissingBean
    AmadeusClients amadeusClients(AmadeusProperties props,
                                  ObjectProvider<JsonMapper> jsonMapper,
                                  ObjectProvider<RestClient.Builder> restClientBuilder,
                                  ObjectProvider<AmadeusRestClientCustomizer> customizers) {
        JsonMapper mapper = jsonMapper.getIfAvailable(() -> JsonMapper.builder().build());
        var errors = new AmadeusErrorHandler(mapper);

        HttpClient http = HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(props.readTimeout());

        // Boot's builder (when present) carries the app's observability/tracing setup.
        RestClient authClient = newBuilder(restClientBuilder)
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory)
                .defaultStatusHandler(HttpStatusCode::isError, errors)
                .build();
        var tokens = new AmadeusTokenManager(
                new RestClientTokenFetcher(authClient, props), props.refreshSkew(), Clock.systemUTC());

        var r = props.retry();
        var retryPolicy = new RetryPolicy(r.maxAttempts(), r.initialBackoff(), r.maxBackoff());

        RestClient.Builder apiBuilder = newBuilder(restClientBuilder)
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(new AmadeusRequestInterceptor(tokens, retryPolicy))
                .defaultStatusHandler(HttpStatusCode::isError, errors);
        customizers.orderedStream().forEach(c -> c.customize(apiBuilder));

        var proxyFactory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(apiBuilder.build()))
                .build();
        return new AmadeusClients(proxyFactory, tokens);
    }

    private static RestClient.Builder newBuilder(ObjectProvider<RestClient.Builder> provider) {
        return provider.getIfAvailable(RestClient::builder).clone();
    }

    // ---- Low-level API clients. Adding a new Amadeus API = new interface + one bean here. ----

    @Bean
    @ConditionalOnMissingBean
    FlightOffersApi flightOffersApi(AmadeusClients clients) {
        return clients.proxyFactory().createClient(FlightOffersApi.class);
    }

    /** The facade apps inject: amadeus.shopping.flightOffersSearch.post(...) */
    @Bean
    @ConditionalOnMissingBean
    Amadeus amadeus(FlightOffersApi offers,
                    ObjectProvider<JsonMapper> jsonMapper) {
        JsonMapper mapper = jsonMapper.getIfAvailable(() -> JsonMapper.builder().build());
        return new Amadeus(offers, new AmadeusJson(mapper));
    }

    /** Problem-detail mapping, only in servlet web apps, can be switched off. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "amadeus.problem-details", name = "enabled", matchIfMissing = true)
    static class WebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        AmadeusExceptionAdvice amadeusExceptionAdvice() {
            return new AmadeusExceptionAdvice();
        }
    }
}
