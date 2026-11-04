package se.comerit.resurs.api.v1.service;

import java.time.Instant;

/**
 * Signing half of the BankID integration: the company's authorised signatory
 * confirms the application before it is submitted.
 *
 * <p>
 * This is the seam a real BankID integration replaces. The contract is a single
 * synchronous call, deliberately, because the two-phase mechanics of the real
 * RP-API -- starting an order, then polling {@code collect} until the signatory
 * has confirmed in the BankID app -- belong to the implementation, not to its
 * callers. A real implementation performs that polling inside {@link #sign}
 * until the order completes or times out, and callers are unchanged.
 *
 * <p>
 * The mock behind it is happy path only: it never throws. Failure is part of
 * the contract anyway -- a real order can be declined or time out -- so
 * implementations signal it with {@link BankIdSigningException}, which the
 * application turns into a 403 and rolls the submission back.
 *
 * @see BankIdService for the authentication half of the same integration
 */
public interface BankIdSigningService {

    /**
     * Signs one application payload on behalf of the company's signatory.
     *
     * @param orgNumber   the organisation number of the company whose signatory
     *                    is signing
     * @param payloadJson the application payload, serialised as JSON -- the exact
     *                    content being signed, so a signature can later be traced
     *                    back to what was agreed to
     * @return the resulting signature; never {@code null}
     * @throws BankIdSigningException if the order was declined, timed out, or
     *                                could not be placed at all
     */
    BankIdSignature sign(String orgNumber, String payloadJson);

    /**
     * One completed signing order.
     *
     * @param orderRef   reference of the signing order, matching the reference a
     *                   real BankID order would be tracked by
     * @param signature  the signature itself. Opaque to this application: it is
     *                   evidence the order completed, not something the portal
     *                   verifies
     * @param signedBy   the signatory who signed, as recorded on the company --
     *                   who signed is as much a part of the record as what was
     *                   signed
     * @param signedAt   when the order completed
     */
    record BankIdSignature(
            String orderRef,
            String signature,
            String signedBy,
            Instant signedAt) {
    }
}
