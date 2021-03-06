package org.cloudfoundry.identity.uaa.db;

import org.junit.Test;

import java.util.List;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.fail;

public class SqlServerDbMigrationIntegrationTest extends DbMigrationIntegrationTestParent {

    private String checkPrimaryKeyExists = "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_CATALOG = ? AND TABLE_NAME = LOWER(?) AND CONSTRAINT_NAME like 'PK_%'";
    private String getAllTableNames = "SELECT distinct TABLE_NAME from information_schema.tables WHERE TABLE_CATALOG = ? and TABLE_NAME != 'schema_version'";
    private String insertNewOauthCodeRecord = "insert into oauth_code(code) values('code');";
    private String fetchColumnTypeFromTable = "SELECT data_type FROM information_schema.columns WHERE table_name = ? and TABLE_CATALOG = ? and column_name = ?";
    private String fetchColumnIsNullableFromTable = "SELECT is_nullable FROM information_schema.columns WHERE table_name = ? and TABLE_CATALOG = ? and column_name = ?";

    @Override
    protected String onlyRunTestsForActiveSpringProfileName() {
        return "sqlserver";
    }

    @Test
    public void everyTableShouldHaveAPrimaryKeyColumn() throws Exception {
        flyway.migrate();

        List<String> tableNames = jdbcTemplate.queryForList(getAllTableNames, String.class, jdbcTemplate.getDataSource().getConnection().getCatalog());
        assertThat(tableNames, hasSize(greaterThan(0)));
        for (String tableName : tableNames) {
            int count = jdbcTemplate.queryForObject(checkPrimaryKeyExists, Integer.class, jdbcTemplate.getDataSource().getConnection().getCatalog(), tableName);
            assertThat(format("%s is missing primary key", tableName), count, greaterThanOrEqualTo(1));
        }

        try {
            jdbcTemplate.execute(insertNewOauthCodeRecord);
        } catch (Exception _) {
            fail("oauth_code table should auto increment primary key when inserting data.");
        }
    }

    @Test
    public void mfaTableAddsTwoNewColumns() {
        MigrationTest migrationTest = new MigrationTest() {
            @Override
            public String getTargetMigration() {
                return "4.13.0";
            }

            @Override
            public void runAssertions() throws Exception {
                String encryptionKeyLabelColumnType = jdbcTemplate.queryForObject(
                  fetchColumnTypeFromTable,
                  String.class,
                  "user_google_mfa_credentials",
                  jdbcTemplate.getDataSource().getConnection().getCatalog(),
                  "encryption_key_label"
                );
                assertThat(encryptionKeyLabelColumnType, is("varchar"));

                String encryptedValidationCodeColumnType = jdbcTemplate.queryForObject(
                  fetchColumnTypeFromTable,
                  String.class,
                  "user_google_mfa_credentials",
                  jdbcTemplate.getDataSource().getConnection().getCatalog(),
                  "encrypted_validation_code"
                );
                assertThat(encryptedValidationCodeColumnType, is("varchar"));

                String encryptedValidationCodeColumnIsNullable = jdbcTemplate.queryForObject(
                  fetchColumnIsNullableFromTable,
                  String.class,
                  "user_google_mfa_credentials",
                  jdbcTemplate.getDataSource().getConnection().getCatalog(),
                  "encrypted_validation_code"
                );
                assertThat(encryptedValidationCodeColumnIsNullable, is("YES"));

                String validationCodeColumnIsNullable = jdbcTemplate.queryForObject(
                  fetchColumnIsNullableFromTable, String.class,
                  "user_google_mfa_credentials",
                  jdbcTemplate.getDataSource().getConnection().getCatalog(),
                  "validation_code"
                );
                assertThat(validationCodeColumnIsNullable, is("YES"));

                String validationColumnType = jdbcTemplate.queryForObject(
                  fetchColumnTypeFromTable,
                  String.class,
                  "user_google_mfa_credentials",
                  jdbcTemplate.getDataSource().getConnection().getCatalog(),
                  "validation_code"
                );
                assertThat(validationColumnType, is("int"));
            }
        };

        migrationTestRunner.run(migrationTest);
    }
}
