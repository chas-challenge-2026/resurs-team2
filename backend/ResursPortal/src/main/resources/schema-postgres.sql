-- PostgreSQL-only schema companions for schema.sql.
--
-- schema.sql itself is portable and is also loaded by the embedded H2 database
-- (the `local` profile and the integration tests). Anything below is real
-- PostgreSQL PL/pgSQL that H2 cannot parse, so it is kept out of schema.sql.
--
-- In production this is provisioned by infra/seed.sql via the postgres image's
-- docker-entrypoint-initdb.d. It is kept in the application resources too so the
-- PostgreSQL integration test (BlockModificationTriggerIT) can apply the exact
-- same DDL as the deployment. If you ever enable Spring SQL init against
-- PostgreSQL, load it with spring.sql.init.platform=postgres, which makes Spring
-- pick up schema-postgres.sql alongside schema.sql.

-- Function to block any modifications (UPDATE, DELETE) to the audit_log table
CREATE FUNCTION block_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

-- UPDATE and DELETE are rejected row by row.
CREATE TRIGGER block_modification_t
BEFORE UPDATE OR DELETE ON audit_log
FOR EACH ROW EXECUTE FUNCTION block_modification();

-- TRUNCATE does not fire row-level triggers, so it is blocked separately.
CREATE TRIGGER block_truncate_t
BEFORE TRUNCATE ON audit_log
FOR EACH STATEMENT EXECUTE FUNCTION block_modification();
