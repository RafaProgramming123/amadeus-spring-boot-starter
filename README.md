# Amadeus Spring Boot Starter

Unofficial Spring Boot 4 / Java 21 client for the **Amadeus Enterprise** travel APIs.
Add one dependency, set two properties, inject `Amadeus`.

- OAuth2 token fetched, cached and refreshed for you (one refresh even under heavy concurrency)
- Retries on 429 and transient errors, never double-books
- Typed DTOs for Flight Offers Search, each offer keeping its original JSON
- Typed `AmadeusException` with the Amadeus error codes

> Not affiliated with or endorsed by Amadeus IT Group. You need your own Amadeus Enterprise credentials.

## Install

```xml
<dependency>
    <groupId>io.github.rafaprogramming123</groupId>
    <artifactId>amadeus-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gradle: `implementation("io.github.rafaprogramming123:amadeus-spring-boot-starter:0.1.0")`

Requires Spring Boot 4.x and Java 21.

## Configure

```yaml
amadeus:
  client-id: ${AMADEUS_CLIENT_ID}
  client-secret: ${AMADEUS_CLIENT_SECRET}
```

The client stays switched off until `amadeus.client-id` is set, so adding the dependency alone never breaks an app.

| Property | Default | |
|---|---|---|
| `amadeus.base-url` | `https://test.travel.api.amadeus.com` | set the production host when you go live |
| `amadeus.token-path` | `/v1/security/oauth2/token` | |
| `amadeus.refresh-skew` | `60s` | refresh this long before the token expires |
| `amadeus.connect-timeout` | `5s` | |
| `amadeus.read-timeout` | `30s` | |
| `amadeus.retry.max-attempts` | `3` | 1 call + 2 retries |
| `amadeus.retry.initial-backoff` | `300ms` | doubles each retry, with jitter |
| `amadeus.retry.max-backoff` | `5s` | also caps `Retry-After` |
| `amadeus.problem-details.enabled` | `true` | map `AmadeusException` to RFC 9457 responses in web apps |

## Use

```java
@Service
class Trips {
    private final Amadeus amadeus;
    Trips(Amadeus amadeus) { this.amadeus = amadeus; }

    Optional<FlightOffer> cheapest() {
        var search = amadeus.shopping.flightOffersSearch.post(
                FlightOffersSearchRequest.oneWay("SKP", "VIE", LocalDate.of(2026, 11, 20), 1, 10, "EUR"));
        return search.cheapest();
    }
}
```

| Call | Endpoint |
|---|---|
| `amadeus.shopping.flightOffersSearch.post(req)` | `POST /v2/shopping/flight-offers` |

### Errors

```java
try {
    amadeus.shopping.flightOffersSearch.post(request);
} catch (AmadeusException e) {
    if (e.hasCode(477)) { /* INVALID FORMAT */ }
    e.status(); e.issues();
}
```

### Customize

Any bean can be replaced by declaring your own (`@ConditionalOnMissingBean`). To tweak the HTTP client:

```java
@Bean
AmadeusRestClientCustomizer amadeusHeaders() {
    return builder -> builder.defaultHeader("X-Request-Source", "my-app");
}
```

## Retry rules

| Response | Search / pricing / GET / DELETE | Booking (POST) |
|---|---|---|
| 401 | refresh token, retry once | refresh token, retry once |
| 429 | retry with backoff | retry with backoff |
| 5xx, network error | retry with backoff | **never**, could double-book |
| other 4xx | fail | fail |

## Contributing a new Amadeus API

1. Add an `@HttpExchange` interface in `api/`.
2. Register it in `AmadeusAutoConfiguration` with one `@Bean`.
3. Expose it on the `Amadeus` facade with a namespace class.
4. Add DTOs under `dto/` and a test.

## Build

This is a standalone single-module project (Java 21, no parent POM).

```bash
mvn verify      # build + tests
mvn install     # into your local ~/.m2, usable by your other projects
```

Run `./rename.sh <your-github-username> "<Your Full Name>"` once before the first build, to
replace the `rafaprogramming123` placeholder in the groupId and the Java package.

A worked example of consuming the starter lives in the separate **amadeus-demo** project. Until
the starter is on Maven Central, run `mvn install` here first, then `mvn spring-boot:run` there.

## Releasing (maintainers)

Push a tag: `git tag v0.2.0 && git push --tags`. The `Release` workflow sets the version with
`mvn versions:set`, then runs `mvn -B -Prelease deploy`, which attaches the sources and javadoc
jars, signs everything with GPG and uploads it to Maven Central. Then click **Publish** in the
Central portal.

To rehearse the release build locally without uploading:

```bash
mvn -Prelease verify     # needs a GPG key: signing runs in the verify phase
```
