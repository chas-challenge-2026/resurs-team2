INSERT INTO companies (org_number, company_name, authorized_signatory) VALUES
('556000-1234', 'Malmö Fastigheter AB', 'Anders Karlsson'),
('556000-5678', 'Göteborg Handel AB', 'Maria Svensson');

INSERT INTO applications (company_id, requested_amount, purpose, status, decision, scoring_result) VALUES
(1, 500000.00, 'Expansion av verksamheten', 'UNDER_REVIEW', null, 'FLAGGED: soliditet=0.28 (OK), likviditetsgrad=0.95 (FLAGGED), skuldsättningsgrad=2.1 (OK)');

INSERT INTO audit_log (application_id, sequence_number, hash, previous_hash, entry, timestamp) VALUES
(1, 1, '', '', '{"action":"APPLICATION_CREATED","orgNumber":"556000-1234"}', '2026-01-15T10:00:00'),
(1, 2, '', '', '{"action":"SCORING_RUN","result":"REVIEW","flags":"1"}', '2026-01-15T10:00:01');
