package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DBConnection — Singleton class for managing JDBC database connection.
 *
 * OOP Concepts Used:
 *   - Encapsulation  : private constructor, private static connection
 *   - Static keyword : single shared instance across entire application
 *   - Final keyword  : constants cannot be accidentally changed
 */
public class DBConnection {

    // ── CONSTANTS ──────────────────────────────────────────────────────────
    // 'final' makes these immutable — cannot be changed after assignment
    // 'static' means they belong to the class, not any object instance

    private static final String URL =
        "jdbc:mysql://localhost:3306/banking_system" +
        "?useSSL=false" +
        "&serverTimezone=UTC" +
        "&allowPublicKeyRetrieval=true" +
        "&useUnicode=true" +
        "&characterEncoding=UTF-8";

    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "1234"; // ← change this

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    // ── SINGLETON INSTANCE ─────────────────────────────────────────────────
    // One shared Connection object for the entire application
    // 'volatile' ensures visibility across threads (safe for multithreading)
    private static volatile Connection connection = null;

    // ── PRIVATE CONSTRUCTOR ────────────────────────────────────────────────
    // Prevents anyone from doing: new DBConnection()
    // Forces use of getConnection() only
    private DBConnection() {
        // Cannot be instantiated
    }

    // ── getConnection() ────────────────────────────────────────────────────
    /**
     * Returns the single shared Connection.
     * Creates it on first call (Lazy Initialization).
     * Re-creates it if the connection was closed or timed out.
     *
     * @return active Connection object
     * @throws SQLException if connection fails
     */
    public static Connection getConnection() throws SQLException {

        // Check if connection is null OR closed/timed out
        if (connection == null || connection.isClosed()) {

            try {
                // Step 1: Load the MySQL JDBC driver class into JVM memory
                // Required for older JDBC versions; optional from JDBC 4.0+
                // but good practice to include explicitly
                Class.forName(DRIVER_CLASS);

                // Step 2: DriverManager creates the actual TCP connection
                // to MySQL using the URL, username, and password
                connection = DriverManager.getConnection(
                    URL,
                    DB_USERNAME,
                    DB_PASSWORD
                );

                System.out.println("[ DB ] Connection established successfully.");

            } catch (ClassNotFoundException e) {
                // JDBC driver JAR is missing from classpath
                throw new SQLException(
                    "MySQL JDBC Driver not found. " +
                    "Ensure mysql-connector-j.jar is added as library.\n" +
                    "Detail: " + e.getMessage()
                );
            }
        }

        return connection;
    }

    // ── closeConnection() ──────────────────────────────────────────────────
    /**
     * Gracefully closes the database connection.
     * Call this only when the application is shutting down (in Main.java).
     */
    public static void closeConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    System.out.println("[ DB ] Connection closed gracefully.");
                }
            } catch (SQLException e) {
                System.err.println("[ DB ] Error closing connection: "
                                   + e.getMessage());
            } finally {
                connection = null; // Allow garbage collection
            }
        }
    }

    // ── testConnection() ───────────────────────────────────────────────────
    /**
     * Quick health-check — useful for debugging setup issues.
     * Run this first to confirm everything is wired correctly.
     */
    public static void testConnection() {
        System.out.println("\n==============================");
        System.out.println("  Testing Database Connection");
        System.out.println("==============================");
        try {
            Connection conn = getConnection();
            if (conn != null && !conn.isClosed()) {
                System.out.println("  Status  : SUCCESS ✓");
                System.out.println("  Database: banking_system");
                System.out.println("  Driver  : " + conn.getMetaData()
                                                        .getDriverName());
                System.out.println("  Version : " + conn.getMetaData()
                                                        .getDatabaseProductVersion());
            }
        } catch (SQLException e) {
            System.err.println("  Status  : FAILED ✗");
            System.err.println("  Reason  : " + e.getMessage());
            System.err.println("\n  Checklist:");
            System.err.println("  [ ] Is MySQL Server running?");
            System.err.println("  [ ] Is the password correct in DBConnection?");
            System.err.println("  [ ] Is banking_system database created?");
            System.err.println("  [ ] Is mysql-connector-j.jar added as library?");
        }
        System.out.println("==============================\n");
    }
}