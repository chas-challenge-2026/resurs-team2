package se.comerit.resurs.config;

/**
 * Encodes/decodes a PII value between the plaintext held by the entity and the
 * value stored in its (character-typed) at-rest column. Production uses an
 * encrypted codec (base64 of the {@code [nonce|cipher]} blob) while the test
 * profile stores and reads plain strings.
 */
public interface PiiCodec {

    String encode(String plaintext);

    String decode(String stored);
}