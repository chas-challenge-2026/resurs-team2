INSERT INTO companies (org_number, org_number_index, company_name, authorized_signatory) VALUES
('556000-1234', X'dedd7d2467a47aac7cc703665899fded7d8013ddecbbbf69e0ff366fd4812ed7', 'Malmö Fastigheter AB', 'Anders Karlsson'),
('556000-5678', X'a4f37788064f1cf726eadc704db91cdc0b1513e482981ff59641e13f518bbbea', 'Göteborg Handel AB', 'Maria Svensson');

INSERT INTO case_workers (name, email, email_index, password) VALUES
('Karin Handläggare', 'karin@resurs.se', X'240cf76b4caf0123ebfc7392cd379b0467f4026fa356c0a05bfa360a87679413', '$2a$10$rUonBwDLz9IA0Ivwnor38.tjZevxSeIHzQx5b4u0RwHhHJ/sbao32');

INSERT INTO applications (company_id, requested_amount, purpose, status, decision, scoring_result, audit_log) VALUES
(1, 500000.00, 'Expansion av verksamheten', 'UNDER_REVIEW', null, 'FLAGGED: soliditet=0.28 (OK)', '[{"ts":"2026-01-15T10:00:00","action":"APPLICATION_CREATED"}]');
