package com.fasterxml.jackson.databind;
public abstract class JsonNode implements Iterable<JsonNode> {
    public JsonNode path(String f) { return null; }
    public JsonNode path(int i) { return null; }
    public String asText(String d) { return d; }
    public boolean asBoolean(boolean d) { return d; }
    public int asInt(int d) { return d; }
    public boolean isArray() { return false; }
    public boolean isEmpty() { return true; }
    public java.util.Iterator<JsonNode> iterator() { return null; }
}
