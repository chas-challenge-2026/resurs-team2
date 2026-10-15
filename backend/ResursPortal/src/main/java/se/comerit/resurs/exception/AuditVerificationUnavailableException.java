package se.comerit.resurs.exception;

/**
 * Audit chain verification was requested, but no public key is configured to verify
 * against (resurs.audit.public-key.path is blank).
 */
public class AuditVerificationUnavailableException extends RuntimeException {

    public AuditVerificationUnavailableException() {
        super("Audit chain verification unavailable: no audit public key configured "
                + "(resurs.audit.public-key.path / RESURS_AUDIT_PUBKEY_PATH)");
    }
}
