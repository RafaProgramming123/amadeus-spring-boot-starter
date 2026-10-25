package io.github.rafaprogramming123.amadeus.autoconfigure;

import io.github.rafaprogramming123.amadeus.Amadeus;
import io.github.rafaprogramming123.amadeus.api.FlightOffersApi;
import io.github.rafaprogramming123.amadeus.http.AmadeusJson;
import io.github.rafaprogramming123.amadeus.error.AmadeusExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks what a consuming app gets just by adding the dependency. */
class AmadeusAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AmadeusAutoConfiguration.class));

    @Test
    void staysOffWithoutCredentials() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(Amadeus.class));
    }

    @Test
    void createsFacadeWithDefaults() {
        runner.withPropertyValues("amadeus.client-id=id", "amadeus.client-secret=secret")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(Amadeus.class);
                    assertThat(ctx).hasSingleBean(FlightOffersApi.class);
                    assertThat(ctx).doesNotHaveBean(RestClient.class);   // internals stay private
                    var props = ctx.getBean(AmadeusProperties.class);
                    assertThat(props.baseUrl()).isEqualTo("https://test.travel.api.amadeus.com");
                    assertThat(props.retry().maxAttempts()).isEqualTo(3);
                });
    }

    @Test
    void failsFastWhenSecretIsMissing() {
        runner.withPropertyValues("amadeus.client-id=id")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void appCanReplaceTheFacade() {
        runner.withPropertyValues("amadeus.client-id=id", "amadeus.client-secret=secret")
                .withUserConfiguration(CustomFacade.class)
                .run(ctx -> assertThat(ctx.getBean(Amadeus.class)).isSameAs(CustomFacade.INSTANCE));
    }

    @Test
    void problemDetailsAdviceOnlyInWebAppsAndCanBeDisabled() {
        runner.withPropertyValues("amadeus.client-id=id", "amadeus.client-secret=secret")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AmadeusExceptionAdvice.class));

        var web = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AmadeusAutoConfiguration.class))
                .withPropertyValues("amadeus.client-id=id", "amadeus.client-secret=secret");
        web.run(ctx -> assertThat(ctx).hasSingleBean(AmadeusExceptionAdvice.class));
        web.withPropertyValues("amadeus.problem-details.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AmadeusExceptionAdvice.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomFacade {
        static Amadeus INSTANCE;

        @Bean
        Amadeus amadeus(FlightOffersApi offers) {
            INSTANCE = new Amadeus(offers, new AmadeusJson(JsonMapper.builder().build()));
            return INSTANCE;
        }
    }
}
