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
    case_worker_id INT REFERENCES case_workers(id),
    requested_amount VARCHAR(512),
    purpose TEXT,
    status VARCHAR(30) DEFAULT 'PENDING_DOCS', -- PENDING_DOCS, SCORING_IN_PROGRESS, UNDER_REVIEW, APPROVED, REJECTED
    decision VARCHAR(20),
    decision_reason TEXT,
    scoring_result TEXT,
    financial_data TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE documents (
    uuid uuid PRIMARY KEY,
    application_id INT REFERENCES applications(id),
    filename VARCHAR(512),
    original_filename VARCHAR(512),
    doc_type VARCHAR(50),
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_log (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    application_id INT NOT NULL REFERENCES applications(id),
    sequence_number BIGINT NOT NULL,
    hash VARCHAR(512) NOT NULL,
    previous_hash VARCHAR(512) NOT NULL,
    entry TEXT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
