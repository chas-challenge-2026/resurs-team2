package se.comerit.resurs.entity;

import java.math.BigDecimal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.stereotype.Component;

import se.comerit.resurs.config.PiiCodec;

/**
 * Converts a request amount to/from its at-rest representation in the character
 * column via the active {@link PiiCodec} (encrypted base64 in production, plain
 * strings under the test profile). The entity and repository stay typed as
 * {@link BigDecimal}; only the persisted column is opaque.
 */
@Component
@Converter(autoApply = false)
public class AmountAttributeConverter implements AttributeConverter<BigDecimal, String> {

    private final PiiCodec codec;

    public AmountAttributeConverter(PiiCodec codec) {
        this.codec = codec;
    }

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        return attribute == null ? null : codec.encode(attribute.toPlainString());
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new BigDecimal(codec.decode(dbData));
    }
}