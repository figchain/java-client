package io.figchain.client;

import org.apache.avro.specific.SpecificRecordBase;

public class TestRecord extends SpecificRecordBase {

    public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"TestRecord\",\"namespace\":\"io.figchain.client\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]}");
    private CharSequence value;

    public TestRecord() {}
    public TestRecord(CharSequence value) { this.value = value; }

    public org.apache.avro.Schema getSchema() { return SCHEMA$; }

    public java.lang.Object get(int field$) {
        switch (field$) {
            case 0: return value;
            default: throw new org.apache.avro.AvroRuntimeException("Bad index");
        }
    }

    public void put(int field$, java.lang.Object value$) {
        switch (field$) {
            case 0: value = (CharSequence)value$; break;
            default: throw new org.apache.avro.AvroRuntimeException("Bad index");
        }
    }
}
