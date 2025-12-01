package io.figchain.client;

import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AvroEncoding {

    public static <T extends SpecificRecord> byte[] serializeBinary(T record) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        org.apache.avro.io.BinaryEncoder encoder = org.apache.avro.io.EncoderFactory.get().binaryEncoder(out, null);
        DatumWriter<T> writer = new SpecificDatumWriter<>(record.getSchema());
        writer.write(record, encoder);
        encoder.flush();
        return out.toByteArray();
    }

    public static <T extends SpecificRecord> byte[] serializeWithSchema(T record) throws IOException {
        DatumWriter<T> writer = new SpecificDatumWriter<>(record.getSchema());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (org.apache.avro.file.DataFileWriter<T> dataFileWriter = new org.apache.avro.file.DataFileWriter<>(writer)) {
            dataFileWriter.create(record.getSchema(), out);
            dataFileWriter.append(record);
        }
        return out.toByteArray();
    }

    public static <T extends SpecificRecord> T deserializeWithSchema(byte[] data, Class<T> clazz) throws IOException {
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
        org.apache.avro.io.DatumReader<T> reader = new org.apache.avro.specific.SpecificDatumReader<>(clazz);
        org.apache.avro.io.Decoder decoder = org.apache.avro.io.DecoderFactory.get().binaryDecoder(data, null);
        return reader.read(null, decoder);
    }
}
