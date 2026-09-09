CREATE TABLE companies (
    id SERIAL PRIMARY KEY,
    org_number VARCHAR(512),
    org_number_index BYTEA UNIQUE,
    company_name VARCHAR(512),
    authorized_signatory VARCHAR(512)
);

CREATE TABLE case_workers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(512),
    email VARCHAR(512),
    email_index BYTEA UNIQUE,
    password VARCHAR(255)
);

CREATE TABLE applications (
    id SERIAL PRIMARY KEY,
    company_id INT REFERENCES companies(id),
    requested_amount VARCHAR(512),
    purpose TEXT,
    status VARCHAR(30) DEFAULT 'PENDING_DOCS', -- PENDING_DOCS, UNDER_REVIEW, APPROVED, REJECTED
    decision VARCHAR(20),
    decision_reason TEXT,
    scoring_result TEXT,
    audit_log TEXT DEFAULT '[]',  -- legacy JSON blob; entries are appended to audit_log table
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    application_id INT REFERENCES applications(id),
    filename VARCHAR(512),
    doc_type VARCHAR(50),
    uploaded_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE audit_log (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    application_id INT NOT NULL REFERENCES applications(id),
    sequence_number BIGINT NOT NULL,
    hash VARCHAR(512) NOT NULL,
    previous_hash VARCHAR(512) NOT NULL,
    entry TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);

-- NOTE: companies, applications and case workers contain PII. Production seeds
-- them (encrypted) via PiiInitializer at application startup, not here.

