CREATE TABLE companies (
    id SERIAL PRIMARY KEY,
    org_number VARCHAR(512),
    org_number_index BYTEA UNIQUE,
    company_name VARCHAR(512),
    authorized_signatory VARCHAR(512)
);

CREATE TABLE case_workers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100) UNIQUE,
    password VARCHAR(255)
);

CREATE TABLE applications (
    id SERIAL PRIMARY KEY,
    company_id INT REFERENCES companies(id),
    requested_amount DECIMAL(15,2),
    purpose TEXT,
    status VARCHAR(30) DEFAULT 'PENDING_DOCS', -- PENDING_DOCS, UNDER_REVIEW, APPROVED, REJECTED
    decision VARCHAR(20),
    decision_reason TEXT,
    scoring_result TEXT,
    audit_log TEXT DEFAULT '[]',  -- JSON blob, no separate table
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    application_id INT REFERENCES applications(id),
    filename VARCHAR(255),
    doc_type VARCHAR(50),
    uploaded_at TIMESTAMP DEFAULT NOW()
);

-- NOTE: companies and applications contain PII. Production seeds them
-- (encrypted) via PiiInitializer at application startup, not here.

-- Case worker (password = "password123")
INSERT INTO case_workers (name, email, password) VALUES
('Karin Handläggare', 'karin@resurs.se', '$argon2id$v=19$m=65536,t=3,p=1$DMdWvwusPFNcQXgjaqLWkA$8wxxrvV1aOBqi+Do+xG9dgVHou2N5Impq4ou3AidxS4');

