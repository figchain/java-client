package io.figchain.client;

import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AvroEncoding {

    private static final java.util.Set<String> TRUSTED_CLASSES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Lets Avro instantiate the given record class and the record, enum and fixed classes nested
     * in its schema. Avro 1.12 only instantiates trusted classes, so that a schema from the
     * network can't make the app load arbitrary classes. Classes the caller passes in were
     * compiled into the app on purpose (e.g. by figchain-maven-plugin), so trusting exactly
     * those keeps the protection without requiring JVM flags.
     */
    static void trust(Class<? extends SpecificRecord> clazz) {
        if (TRUSTED_CLASSES.contains(clazz.getName())) {
            return;
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        names.add(clazz.getName());
        collectNamedTypes(org.apache.avro.specific.SpecificData.get().getSchema(clazz), names, new java.util.HashSet<>());
        TRUSTED_CLASSES.addAll(names);
        installPredicate();
    }

    // The client's own wire-protocol models (figchain-models) are always trusted.
    private static final String CLIENT_MODEL_PACKAGE = "io.figchain.avro.model.";
    private static volatile boolean predicateInstalled;

    private static synchronized void installPredicate() {
        if (predicateInstalled) {
            return;
        }
        // ClassSecurityValidator.composite requires *all* predicates to agree, so it can't widen
        // trust; combine with the existing global predicate as an explicit OR instead.
        org.apache.avro.util.ClassSecurityValidator.ClassSecurityPredicate existing =
                org.apache.avro.util.ClassSecurityValidator.getGlobal();
        org.apache.avro.util.ClassSecurityValidator.setGlobal(new org.apache.avro.util.ClassSecurityValidator.ClassSecurityPredicate() {
            @Override
            public boolean isTrusted(Class<?> c) {
                return TRUSTED_CLASSES.contains(c.getName()) || c.getName().startsWith(CLIENT_MODEL_PACKAGE) || existing.isTrusted(c);
            }

            @Override
            public void forbiddenClass(String className) {
                existing.forbiddenClass(className);
            }
        });
        predicateInstalled = true;
    }

    static {
        installPredicate();
    }

    private static void collectNamedTypes(org.apache.avro.Schema schema, java.util.Set<String> names, java.util.Set<org.apache.avro.Schema> seen) {
        if (!seen.add(schema)) {
            return;
        }
        switch (schema.getType()) {
            case RECORD -> {
                names.add(schema.getFullName());
                schema.getFields().forEach(f -> collectNamedTypes(f.schema(), names, seen));
            }
            case ENUM, FIXED -> names.add(schema.getFullName());
            case ARRAY -> collectNamedTypes(schema.getElementType(), names, seen);
            case MAP -> collectNamedTypes(schema.getValueType(), names, seen);
            case UNION -> schema.getTypes().forEach(t -> collectNamedTypes(t, names, seen));
            default -> { }
        }
    }

    public static <T extends SpecificRecord> byte[] serializeBinary(T record) throws IOException {
        trust(record.getClass());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        org.apache.avro.io.BinaryEncoder encoder = org.apache.avro.io.EncoderFactory.get().binaryEncoder(out, null);
        DatumWriter<T> writer = new SpecificDatumWriter<>(record.getSchema());
        writer.write(record, encoder);
        encoder.flush();
        return out.toByteArray();
    }

    public static <T extends SpecificRecord> byte[] serializeWithSchema(T record) throws IOException {
        trust(record.getClass());
        DatumWriter<T> writer = new SpecificDatumWriter<>(record.getSchema());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (org.apache.avro.file.DataFileWriter<T> dataFileWriter = new org.apache.avro.file.DataFileWriter<>(writer)) {
            dataFileWriter.create(record.getSchema(), out);
            dataFileWriter.append(record);
        }
        return out.toByteArray();
    }

    public static <T extends SpecificRecord> T deserializeWithSchema(byte[] data, Class<T> clazz) throws IOException {
        trust(clazz);
        org.apache.avro.io.DatumReader<T> reader = new org.apache.avro.specific.SpecificDatumReader<>(clazz);
        try (org.apache.avro.file.DataFileReader<T> dataFileReader = new org.apache.avro.file.DataFileReader<>(new org.apache.avro.file.SeekableByteArrayInput(data), reader)) {
            if (dataFileReader.hasNext()) {
                return dataFileReader.next();
            } else {
                throw new IOException("No Avro data found in container file");
            }
        }
    }

    public static <T extends SpecificRecord> T deserializeBinary(byte[] data, Class<T> clazz) throws IOException {
        return deserializeBinary(data, clazz, null);
    }

    public static <T extends SpecificRecord> T deserializeBinary(byte[] data, Class<T> clazz, org.apache.avro.Schema writerSchema) throws IOException {
        trust(clazz);
        org.apache.avro.io.DatumReader<T> reader;
        if (writerSchema != null) {
            org.apache.avro.Schema readerSchema = org.apache.avro.specific.SpecificData.get().getSchema(clazz);
            reader = new org.apache.avro.specific.SpecificDatumReader<>(writerSchema, readerSchema);
        } else {
            reader = new org.apache.avro.specific.SpecificDatumReader<>(clazz);
        }
        org.apache.avro.io.Decoder decoder = org.apache.avro.io.DecoderFactory.get().binaryDecoder(data, null);
        return reader.read(null, decoder);
    }
}
