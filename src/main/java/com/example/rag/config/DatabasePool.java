package com.example.rag.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Shared SQLite connection pool — replaces per-store pools.
 * All stores borrow from this single pool, reducing total connections from 14 to 4.
 */
public final class DatabasePool {

    private static final String DB_URL = "jdbc:sqlite:chat.db";
    private static final int POOL_SIZE = 6;
    private static final BlockingQueue<Connection> pool = new LinkedBlockingQueue<>(POOL_SIZE);

    static {
        for (int i = 0; i < POOL_SIZE; i++) {
            try {
                pool.offer(createRawConnection());
            } catch (SQLException e) {
                System.err.println("[WARN] Failed to init DatabasePool connection: " + e.getMessage());
            }
        }
        System.out.println("[INIT] DatabasePool initialized with " + POOL_SIZE + " connections");
    }

    private DatabasePool() {}

    private static Connection createRawConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA busy_timeout=5000");
        }
        return conn;
    }

    /**
     * Borrow a pooled connection. close() returns it to the pool via proxy wrapper,
     * so try-with-resources works transparently.
     */
    public static Connection getConnection() throws SQLException {
        Connection raw;
        try {
            raw = pool.poll(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return createRawConnection();
        }
        if (raw == null || raw.isClosed()) {
            raw = createRawConnection();
        }
        final Connection delegate = raw;
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
                        try {
                            if (!delegate.isClosed()) pool.offer(delegate);
                        } catch (SQLException ignored) {}
                        return null;
                    }
                    return method.invoke(delegate, args);
                }
        );
    }
}
