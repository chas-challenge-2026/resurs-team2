package se.comerit.resurs.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test codec: stores and reads PII as plain strings, so tests can keep using
 * original plaintext fixtures and assertions.
 */
@Component
@Profile("test")
public class PlainPiiCodec implements PiiCodec {

    @Override
    public String encode(String plaintext) {
        return plaintext;
    }

    @Override
    public String decode(String stored) {
        return stored;
    }
}