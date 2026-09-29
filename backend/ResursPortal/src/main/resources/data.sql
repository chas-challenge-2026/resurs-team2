INSERT INTO companies (org_number, company_name, authorized_signatory) VALUES
('556000-1234', 'Malmö Fastigheter AB', 'Anders Karlsson'),
('556000-5678', 'Göteborg Handel AB', 'Maria Svensson');

INSERT INTO applications (company_id, requested_amount, purpose, status, decision, scoring_result) VALUES
(1, 500000.00, 'Expansion av verksamheten', 'UNDER_REVIEW', null, 'FLAGGED: soliditet=0.28 (OK), likviditetsgrad=0.95 (FLAGGED), skuldsättningsgrad=2.1 (OK)');

INSERT INTO audit_log (application_id, sequence_number, hash, signature, entry, timestamp) VALUES
(1, 1, decode('00641acb9d50478cbf77ee0b17396ae699dde010e9afb2f528e791d7cd300b85', 'hex'), decode('aebd3ce2ac3812e2d7dd73b0e088bd1aa7870944126c5a59f20e6827f788f644d9baea9ab5c127c131c95818713af1900201c1d1b45fb30c1dd16b11688b46f2', 'hex'), '{"action":"APPLICATION_CREATED","orgNumber":"556000-1234"}', '2026-01-15T10:00:00'),
(1, 2, decode('9726d13dc5d91f8009b5c2ef4517186689312e6502837a47d6bf4535b6caf519', 'hex'), decode('78b68aa64016a4f451dfd44c0b5ad28dd2f10d45c4a65c415b8cda786625a2de57208629ee5859cd1928b5e3f6f86a999586d36f1077b06278f2c4d32fb89309', 'hex'), '{"action":"SCORING_RUN","result":"REVIEW","flags":"1"}', '2026-01-15T10:00:01');
