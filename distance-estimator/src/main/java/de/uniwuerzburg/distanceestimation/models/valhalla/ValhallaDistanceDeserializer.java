package de.uniwuerzburg.distanceestimation.models.valhalla;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;

import java.io.IOException;

public class ValhallaDistanceDeserializer extends JsonDeserializer<DistanceEstimate> {
    @Override
    public DistanceEstimate deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        return DistanceEstimate.byKm(jsonParser.getDoubleValue());
    }
}
