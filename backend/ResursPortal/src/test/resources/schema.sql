CREATE TABLE companies (
    id SERIAL PRIMARY KEY,
    org_number VARCHAR(20) UNIQUE,
    company_name VARCHAR(200),
    authorized_signatory VARCHAR(100)
);

CREATE TABLE case_workers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100) UNIQUE,
    password VARCHAR(60)
);

CREATE TABLE applications (
    id SERIAL PRIMARY KEY,
    company_id INT REFERENCES companies(id),
    requested_amount DECIMAL(15,2),
    purpose TEXT,
    status VARCHAR(30) DEFAULT 'PENDING_DOCS',
    decision VARCHAR(20),
    decision_reason TEXT,
    scoring_result TEXT,
    audit_log TEXT DEFAULT '[]',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    application_id INT REFERENCES applications(id),
    filename VARCHAR(255),
    doc_type VARCHAR(50),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
