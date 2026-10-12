INSERT INTO companies (org_number, company_name, authorized_signatory) VALUES
('556000-1234', 'Malmö Fastigheter AB', 'Anders Karlsson'),
('556000-5678', 'Göteborg Handel AB', 'Maria Svensson');

INSERT INTO applications (company_id, requested_amount, purpose, status, decision, scoring_result) VALUES
(1, 500000.00, 'Expansion av verksamheten', 'UNDER_REVIEW', null, 'FLAGGED: soliditet=0.28 (OK), likviditetsgrad=0.95 (FLAGGED), skuldsättningsgrad=2.1 (OK)');

-- The audit entries are inserted unsigned, exactly as they arrive: hash and signature
-- are empty placeholders. PiiInitializer signs them in chain order at startup, the
-- same way it encrypts the plaintext rows above, so the chain verifies end to end.
INSERT INTO audit_log (application_id, sequence_number, hash, signature, entry, timestamp) VALUES
(1, 1, X'', X'', '{"action":"APPLICATION_CREATED","orgNumber":"556000-1234"}', '2026-01-15T10:00:00'),
(1, 2, X'', X'', '{"action":"SCORING_RUN","result":"REVIEW","flags":"1"}', '2026-01-15T10:00:01');
