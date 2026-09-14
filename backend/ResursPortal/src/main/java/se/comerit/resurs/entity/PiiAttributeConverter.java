package se.comerit.resurs.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.stereotype.Component;

import se.comerit.resurs.config.PiiCodec;

/**
 * Converts a plaintext PII field to/from its at-rest representation in the
 * character column via the active {@link PiiCodec} (encrypted base64 in
 * production, plain strings under the test profile).
 */
@Component
@Converter(autoApply = false)
public class PiiAttributeConverter implements AttributeConverter<String, String> {

    private final PiiCodec codec;

    public PiiAttributeConverter(PiiCodec codec) {
        this.codec = codec;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : codec.encode(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : codec.decode(dbData);
    }
}