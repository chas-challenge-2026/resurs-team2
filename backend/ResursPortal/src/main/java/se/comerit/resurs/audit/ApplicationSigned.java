package se.comerit.resurs.audit;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Records that the company's authorised signatory signed the application
 * through BankID, in the step immediately before submission.
 *
 * <p>
 * The entry carries who signed, which the {@code applications} row deliberately
 * does not: the signatory is PII and is already held (encrypted) on
 * {@code companies}, so recording it once here -- beside the organisation
 * number, which every other entry already carries -- avoids a second plaintext
 * copy of the same name.
 *
 * @param orgNumber the company being signed for
 * @param signedBy  the authorised signatory who signed
 * @param orderRef  reference of the BankID signing order, matching
 *                  {@code applications.bankid_signature}'s source order so the
 *                  audit trail and the row can be tied together
 */
public record ApplicationSigned(
        @JsonProperty("orgNumber") String orgNumber,
        @JsonProperty("signedBy") String signedBy,
        @JsonProperty("orderRef") String orderRef) implements AuditEntry {
}
