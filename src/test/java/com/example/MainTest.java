package com.example;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.dbunit.Assertion;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.csv.CsvDataSet;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.ext.h2.H2DataTypeFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MainTest {

    private static final String JDBC_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";

    private static class CustomDataTypeFactory extends H2DataTypeFactory {
        @Override
        public DataType createDataType(int sqlType, String sqlTypeName) throws DataTypeException {
            if (sqlType == Types.TIMESTAMP_WITH_TIMEZONE) {
                return DataType.TIMESTAMP;
            }
            return super.createDataType(sqlType, sqlTypeName);
        }
    }

    private static class Tester extends JdbcDatabaseTester {

        public Tester(String driverClass, String connectionUrl)
                throws ClassNotFoundException {
            super(driverClass, connectionUrl);
        }

        public IDatabaseConnection getConnection() throws Exception {
            IDatabaseConnection connection = super.getConnection();
            connection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new CustomDataTypeFactory());
            return connection;
        }
    }

    @BeforeAll
    static void createTable() throws Exception {

        final String ddl = """
                CREATE TABLE T (
                    ID INTEGER PRIMARY KEY,
                    TIM TIMESTAMP WITH TIME ZONE
                );
                """;

        try (Connection connection = DriverManager.getConnection(JDBC_URL);
                Statement statement = connection.createStatement();) {
            statement.execute(ddl);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    void test() throws Exception {

        // setup
        Tester tester = new Tester("org.h2.Driver", JDBC_URL);
        CsvDataSet dataSet = new CsvDataSet(Paths.get("src/test/resources").toFile());
        tester.setDataSet(dataSet);
        tester.onSetup();

        logData();

        // assertion with ID=1: 2026-07-22T15:00:00 -02:00 will success with dbunit
        // 3.1.0 but fails with 3.2.0
        OffsetDateTime id1 = OffsetDateTime.of(2026, 7, 22, 15, 0, 0, 0, ZoneOffset.ofHours(-2));
        Timestamp exp1 = Timestamp.from(id1.toInstant());
        ZonedDateTime exp2 = id1.atZoneSameInstant(ZoneOffset.UTC);

        Timestamp act1 = getTimestamp(1);
        ZonedDateTime act2 = getOffsetDateTime(1).atZoneSameInstant(ZoneOffset.UTC);
        IDatabaseConnection connection = tester.getConnection();
        IDataSet act3 = connection.createDataSet(new String[] { "T" });

        try {
            assertAll(() -> assertEquals(exp1, act1, "This will fail with DBUnit 3.2.0"),
                    () -> assertEquals(exp2, act2, "This will fail with DBUnit 3.2.0"),
                    () -> Assertion.assertEquals(dataSet, act3));
        } finally {
            connection.close();
        }
    }

    private Timestamp getTimestamp(int id) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL);
                PreparedStatement ps = connection.prepareStatement("SELECT TIM FROM T WHERE ID = ?");) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            rs.next();
            Timestamp res = rs.getTimestamp(1);
            rs.close();
            return res;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private OffsetDateTime getOffsetDateTime(int id) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL);
                PreparedStatement ps = connection.prepareStatement("SELECT TIM FROM T WHERE ID = ?");) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            rs.next();
            OffsetDateTime res = (OffsetDateTime) rs.getObject(1);
            rs.close();
            return res;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void logData() throws Exception {

        try (Connection connection = DriverManager.getConnection(JDBC_URL);
                PreparedStatement ps = connection.prepareStatement("SELECT * FROM T");
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                System.out.println(
                        String.format("ID: %d, TIMESTAMP WITH TIME ZONE: %s", rs.getInt("ID"), rs.getObject("TIM")));
            }
        }

    }
}