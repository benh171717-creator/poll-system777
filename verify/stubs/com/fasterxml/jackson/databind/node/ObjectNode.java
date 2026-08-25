package com.fasterxml.jackson.databind.node;
import com.fasterxml.jackson.databind.JsonNode;
public class ObjectNode extends JsonNode {
    public ObjectNode put(String f, String v) { return this; }
    public ObjectNode put(String f, double v) { return this; }
    public ArrayNode putArray(String f) { return null; }
    public ObjectNode putObject(String f) { return null; }
}
