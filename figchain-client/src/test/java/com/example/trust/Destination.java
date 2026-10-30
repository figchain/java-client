package com.example.trust;

import org.apache.avro.specific.SpecificRecordBase;

public class Destination extends SpecificRecordBase {

    public static final org.apache.avro.Schema SCHEMA$ = Shipment.SCHEMA$.getField("to").schema();
    private CharSequence city;

    public Destination() {}
    public Destination(CharSequence city) { this.city = city; }

    public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }
    public org.apache.avro.Schema getSchema() { return SCHEMA$; }
    public CharSequence getCity() { return city; }

    public java.lang.Object get(int field$) {
        if (field$ == 0) return city;
        throw new org.apache.avro.AvroRuntimeException("Bad index");
    }

    public void put(int field$, java.lang.Object value$) {
        if (field$ == 0) { city = (CharSequence) value$; return; }
        throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
}
