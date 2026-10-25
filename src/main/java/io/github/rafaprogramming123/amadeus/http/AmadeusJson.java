package io.github.rafaprogramming123.amadeus.http;

import io.github.rafaprogramming123.amadeus.dto.common.FlightOffer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON helpers used by the facade to give you typed DTOs WITHOUT losing data.
 *
 * Reading: the API interfaces return JsonNode; we map it to DTOs and attach each
 * offer's original node ({@link FlightOffer#raw()}).
 * Writing: when an offer goes back to pricing/booking we send that original node,
 * falling back to the typed fields only if it isn't there.
 */
public class AmadeusJson {

    private final JsonMapper mapper;

    public AmadeusJson(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public <T> T read(JsonNode node, Class<T> type) {
        return mapper.treeToValue(node, type);
    }

    /** Maps a JSON array of offers to DTOs, each remembering its original JSON. */
    public List<FlightOffer> offersWithRaw(JsonNode offersArray) {
        if (offersArray == null || !offersArray.isArray()) {
            return List.of();
        }
        List<FlightOffer> offers = new ArrayList<>(offersArray.size());
        for (int i = 0; i < offersArray.size(); i++) {
            JsonNode node = offersArray.get(i);
            offers.add(mapper.treeToValue(node, FlightOffer.class).withRaw(node));
        }
        return List.copyOf(offers);
    }

    /** What actually gets sent to Amadeus for an offer. */
    public JsonNode toWire(FlightOffer offer) {
        return offer.raw() != null ? offer.raw() : mapper.valueToTree(offer);
    }

    public List<JsonNode> toWire(List<FlightOffer> offers) {
        return offers.stream().map(this::toWire).toList();
    }
}
