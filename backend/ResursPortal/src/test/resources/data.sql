INSERT INTO companies (org_number, company_name, authorized_signatory) VALUES
('556000-1234', 'Malmö Fastigheter AB', 'Anders Karlsson'),
('556000-5678', 'Göteborg Handel AB', 'Maria Svensson');

INSERT INTO case_workers (name, email, password_md5) VALUES
('Karin Handläggare', 'karin@resurs.se', '482c811da5d5b4bc6d497ffa98491e38');

INSERT INTO applications (company_id, requested_amount, purpose, status, decision, scoring_result, audit_log) VALUES
(1, 500000.00, 'Expansion av verksamheten', 'UNDER_REVIEW', null, 'FLAGGED: soliditet=0.28 (OK)', '[{"ts":"2026-01-15T10:00:00","action":"APPLICATION_CREATED"}]');
