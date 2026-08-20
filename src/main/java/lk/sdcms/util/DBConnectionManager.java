package lk.sdcms.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Provides pooled JDBC connections to the rest of the application.
 *
 * <p><b>Singleton pattern.</b> Establishing a database connection is expensive —
 * a TCP handshake, authentication, and session setup. Opening one per request
 * would exhaust the database's connection limit under clinic load. Exactly one
 * pool must therefore exist for the lifetime of the application.
 *
 * <p><b>Why an enum.</b> The textbook lazy singleton
 * ({@code if (instance == null) instance = new X();}) is not thread-safe: two
 * threads can both evaluate the null check before either assigns, and two pools
 * get created. Double-checked locking fixes that but requires {@code volatile}
 * and is easy to get subtly wrong. A single-element enum is initialised by the
 * JVM's class-loading mechanism, which is guaranteed thread-safe, and is
 * additionally immune to reflection and serialisation attacks. This is the
 * implementation Joshua Bloch recommends in <i>Effective Java</i>.
 */
public enum DBConnectionManager {

    INSTANCE;

    private final HikariDataSource dataSource;

    DBConnectionManager() {
        Properties props = loadProperties();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getProperty("db.url"));
        config.setUsername(props.getProperty("db.user"));
        config.setPassword(props.getProperty("db.password"));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        config.setMaximumPoolSize(
                Integer.parseInt(props.getProperty("db.pool.maxSize", "10")));
        config.setMinimumIdle(
                Integer.parseInt(props.getProperty("db.pool.minIdle", "2")));
        config.setConnectionTimeout(30_000);
        config.setPoolName("SdcmsPool");

        // Fail fast at startup rather than on the first user request.
        config.setInitializationFailTimeout(10_000);

        this.dataSource = new HikariDataSource(config);
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = DBConnectionManager.class
                .getClassLoader()
                .getResourceAsStream("db.properties")) {

            if (in == null) {
                throw new IllegalStateException(
                        "db.properties not found on the classpath. Copy "
                        + "db.properties.example to src/main/resources/db.properties "
                        + "and fill in your credentials.");
            }
            props.load(in);

        } catch (IOException e) {
            throw new IllegalStateException("Unable to read db.properties", e);
        }
        return props;
    }

    public static DBConnectionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Borrows a connection from the pool. The caller <b>must</b> close it —
     * always with try-with-resources. Closing returns it to the pool rather
     * than tearing down the underlying socket. A leaked connection is never
     * returned, and once the pool is drained the application stops responding.
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /** Called from the servlet context listener when the application shuts down. */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
