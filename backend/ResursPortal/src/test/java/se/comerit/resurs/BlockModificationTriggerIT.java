package se.comerit.resurs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Verifies that {@code audit_log} really is append-only in PostgreSQL.
 *
 * <p>The guard is a {@code plpgsql} trigger ({@code block_modification()} in
 * {@code src/main/resources/schema-postgres.sql}), which H2's {@code MODE=PostgreSQL}
 * cannot emulate - so this test runs against a real PostgreSQL started with
 * Testcontainers and applies the production schema verbatim ({@code schema.sql}
 * for the tables plus {@code schema-postgres.sql} for the guard). That is also
 * why nothing here is done through a Spring context: the point is the schema,
 * not the application.</p>
 *
 * <p>Assertions mirror the contract: {@code INSERT} stays open, while
 * {@code UPDATE}, {@code DELETE} and - crucially - {@code TRUNCATE} (which
 * row-level triggers never see) must raise and leave the data untouched.
 * Reading the row back after every rejected write is what tells a loudly
 * rejected operation apart from the old silent {@code RETURN NULL} no-op.</p>
 *
 * <p>The class name ends in {@code IT} so the default Surefire run of
 * {@code ./mvnw test} skips it (Docker requirement); run it explicitly with
 * {@code ./mvnw -Dtest=BlockModificationTriggerIT test}.</p>
 */
@Testcontainers
class BlockModificationTriggerIT {

    private static final String ORIGINAL_ENTRY = "original entry";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static Connection connection;
    private static long applicationId;

    @BeforeAll
    static void setUp() throws Exception {
        applySchema();
        connection = POSTGRES.createConnection("");
        seed();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void insertStaysOpen() throws SQLException {
        int before = rowCount();

        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO audit_log (application_id, sequence_number, hash, signature, entry) "
                    + "VALUES (" + applicationId + ", 2, decode('03', 'hex'), decode('04', 'hex'), 'second entry')");
        }

        assertThat(rowCount()).isEqualTo(before + 1);
    }

    @Test
    void updateIsRejected() throws SQLException {
        assertBlocked("UPDATE audit_log SET entry = 'tampered' WHERE application_id = " + applicationId, "UPDATE");
        assertRowUntouched();
    }

    @Test
    void deleteIsRejected() throws SQLException {
        assertBlocked("DELETE FROM audit_log WHERE application_id = " + applicationId + " AND sequence_number = 1",
                "DELETE");
        assertRowUntouched();
    }

    /**
     * Row-level triggers never fire for TRUNCATE; only the statement-level
     * {@code block_truncate_t} trigger protects against it.
     */
    @Test
    void truncateIsRejected() throws SQLException {
        assertBlocked("TRUNCATE audit_log", "TRUNCATE");
        assertRowUntouched();
    }

    private static void assertBlocked(String sql, String operation) {
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        })
                .isInstanceOf(PSQLException.class)
                .hasMessageContaining("audit_log is append-only")
                .hasMessageContaining(operation + " is not allowed");
    }

    private static void assertRowUntouched() throws SQLException {
        assertThat(entryOf(1)).isEqualTo(ORIGINAL_ENTRY);
        assertThat(rowCount()).as("rejected operation must not have removed rows").isGreaterThanOrEqualTo(1);
    }

    private static int rowCount() throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM audit_log")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static String entryOf(long sequenceNumber) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT entry FROM audit_log "
                        + "WHERE application_id = " + applicationId + " AND sequence_number = " + sequenceNumber)) {
            result.next();
            return result.getString(1);
        }
    }

    /** Applies the production schema files verbatim via psql inside the container. */
    private static void applySchema() throws Exception {
        List<String> command = new ArrayList<>(List.of("psql", "-v", "ON_ERROR_STOP=1",
                "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName()));
        for (String schemaFile : List.of("schema.sql", "schema-postgres.sql")) {
            Path schema = schemaPath(schemaFile);
            String containerPath = "/tmp/" + schemaFile;
            POSTGRES.copyFileToContainer(MountableFile.forHostPath(schema), containerPath);
            command.add("-f");
            command.add(containerPath);
        }

        ExecResult result = POSTGRES.execInContainer(command.toArray(String[]::new));

        assertThat(result.getExitCode())
                .as("applying the production schema failed:%n%s%n%s", result.getStdout(), result.getStderr())
                .isZero();
    }

    private static void seed() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO companies (org_number, company_name) "
                    + "VALUES ('556000-1111', 'Trigger Test AB')",
                    Statement.RETURN_GENERATED_KEYS);
            long companyId = generatedKey(statement);

            statement.execute("INSERT INTO applications (company_id, requested_amount, purpose, status) "
                    + "VALUES (" + companyId + ", '100000', 'append-only trigger test', 'PENDING_DOCS')",
                    Statement.RETURN_GENERATED_KEYS);
            applicationId = generatedKey(statement);

            // Hash and signature are irrelevant to the trigger; non-null values satisfy the NOT NULL columns.
            statement.execute("INSERT INTO audit_log (application_id, sequence_number, hash, signature, entry) "
                    + "VALUES (" + applicationId + ", 1, decode('01', 'hex'), decode('02', 'hex'), '"
                    + ORIGINAL_ENTRY + "')");
        }
    }

    private static long generatedKey(Statement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }

    private static Path schemaPath(String fileName) {
        // Surefire runs with the module directory as working dir; fall back to repo-root relative.
        Path[] candidates = {
                Path.of("src/main/resources", fileName),
                Path.of("backend/ResursPortal/src/main/resources", fileName),
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate " + fileName + " (tried the above paths)");
    }
}