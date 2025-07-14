package de.uniwuerzburg.distanceestimation.models;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class DistanceEstimateSerializer extends JsonSerializer<DistanceEstimate> {

    @Override
    public void serialize(DistanceEstimate distanceEstimate, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeNumber(distanceEstimate.getMeters());
    }
}