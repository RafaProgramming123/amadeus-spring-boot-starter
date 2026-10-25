package io.github.rafaprogramming123.amadeus.dto.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Amadeus warning/error entry. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Issue(Integer status, Integer code, String title, String detail, Source source) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(String pointer, String parameter, String example) {}
}
