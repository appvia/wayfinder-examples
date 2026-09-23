package com.example.messages.messaging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Conversion;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.apache.avro.util.ClassSecurityValidator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.MimeType;

/**
 * Writes and reads Avro records as plain Avro binary for the {@code application/avro}
 * content type. There is no schema registry and no single-object header: the schema
 * is the generated class on each side.
 *
 * <p>A record that does not match its schema, such as one with a required field left
 * null, is refused with a {@link MessageConversionException} that names the schema,
 * and nothing is sent.
 */
public class AvroMessageConverter extends AbstractMessageConverter {

    public static final MimeType APPLICATION_AVRO = MimeType.valueOf("application/avro");

    private static final SchemaValidator VALIDATOR = new SchemaValidator();

    /** Where the classes generated from src/main/avro live. */
    static final String EVENTS_PACKAGE = "com.example.messages.events";

    static {
        trustEventClasses();
    }

    /**
     * Avro 1.12 refuses to load any class named in a schema unless it is trusted, and
     * it loads the generated classes by name to read and write them. This trusts the
     * package the schemas generate into, on top of Avro's own list.
     */
    public static synchronized void trustEventClasses() {
        ClassSecurityValidator.ClassSecurityPredicate current = ClassSecurityValidator.getGlobal();
        if (current instanceof EventClasses) {
            return;
        }
        ClassSecurityValidator.setGlobal(new EventClasses(current));
    }

    private record EventClasses(ClassSecurityValidator.ClassSecurityPredicate fallback)
            implements ClassSecurityValidator.ClassSecurityPredicate {
        @Override
        public boolean isTrusted(Class<?> clazz) {
            return clazz.getPackageName().equals(EVENTS_PACKAGE) || fallback.isTrusted(clazz);
        }
    }

    public AvroMessageConverter() {
        super(APPLICATION_AVRO);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return SpecificRecord.class.isAssignableFrom(clazz);
    }

    @Override
    protected Object convertToInternal(Object payload, MessageHeaders headers, Object conversionHint) {
        SpecificRecord record = (SpecificRecord) payload;
        Schema schema = record.getSchema();
        if (!VALIDATOR.validate(schema, record)) {
            throw new MessageConversionException(
                    "Refusing to send a " + schema.getFullName() + " that does not match its Avro schema: " + record);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            new SpecificDatumWriter<SpecificRecord>(schema).write(record, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (IOException | AvroRuntimeException | NullPointerException | ClassCastException e) {
            throw new MessageConversionException(
                    "Could not write a " + schema.getFullName() + " as Avro binary: " + e.getMessage(), e);
        }
    }

    @Override
    protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object conversionHint) {
        if (!(message.getPayload() instanceof byte[] bytes)) {
            return null;
        }
        try {
            SpecificDatumReader<?> reader = new SpecificDatumReader<>(targetClass.asSubclass(SpecificRecord.class));
            return reader.read(null, DecoderFactory.get().binaryDecoder(bytes, null));
        } catch (IOException | AvroRuntimeException e) {
            throw new MessageConversionException(
                    message, "Could not read the payload as Avro binary " + targetClass.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * {@link org.apache.avro.generic.GenericData#validate} checks the raw Avro types, so
     * on its own it rejects the {@code Instant} and {@code LocalDate} a generated class
     * holds for timestamp and date fields, and the Java enum it holds for an Avro enum.
     * This validator turns a logical-type value back into its raw type before checking
     * it, and {@link SpecificData} accepts Java enums.
     */
    static final class SchemaValidator extends SpecificData {

        SchemaValidator() {
            addLogicalTypeConversion(new TimeConversions.DateConversion());
            addLogicalTypeConversion(new TimeConversions.TimeMillisConversion());
            addLogicalTypeConversion(new TimeConversions.TimeMicrosConversion());
            addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());
            addLogicalTypeConversion(new TimeConversions.TimestampMicrosConversion());
            addLogicalTypeConversion(new TimeConversions.LocalTimestampMillisConversion());
            addLogicalTypeConversion(new TimeConversions.LocalTimestampMicrosConversion());
            addLogicalTypeConversion(new Conversions.DecimalConversion());
            addLogicalTypeConversion(new Conversions.UUIDConversion());
        }

        @Override
        public boolean validate(Schema schema, Object datum) {
            LogicalType logicalType = schema.getLogicalType();
            if (logicalType != null && datum != null) {
                Conversion<Object> conversion = getConversionFor(logicalType);
                if (conversion != null && conversion.getConvertedType().isInstance(datum)) {
                    return super.validate(schema, Conversions.convertToRawType(datum, schema, logicalType, conversion));
                }
            }
            return super.validate(schema, datum);
        }
    }
}
