package com.example.trust;

import org.apache.avro.specific.SpecificRecordBase;

/** Hand-written stand-in for a plugin-generated record outside io.figchain, with a nested record. */
public class Shipment extends SpecificRecordBase {

    public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse(
            "{\"type\":\"record\",\"name\":\"Shipment\",\"namespace\":\"com.example.trust\",\"fields\":["
            + "{\"name\":\"id\",\"type\":\"string\"},"
            + "{\"name\":\"to\",\"type\":{\"type\":\"record\",\"name\":\"Destination\",\"fields\":[{\"name\":\"city\",\"type\":\"string\"}]}}]}");
    private CharSequence id;
    private Destination to;

    public Shipment() {}
    public Shipment(CharSequence id, Destination to) { this.id = id; this.to = to; }

    public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }
    public org.apache.avro.Schema getSchema() { return SCHEMA$; }
    public Destination getTo() { return to; }

    public java.lang.Object get(int field$) {
        switch (field$) {
            case 0: return id;
            case 1: return to;
            default: throw new org.apache.avro.AvroRuntimeException("Bad index");
        }
    }

    public void put(int field$, java.lang.Object value$) {
        switch (field$) {
            case 0: id = (CharSequence) value$; break;
            case 1: to = (Destination) value$; break;
            default: throw new org.apache.avro.AvroRuntimeException("Bad index");
        }
    }
}
