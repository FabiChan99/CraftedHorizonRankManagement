package me.fabichan.craftedHorizonRankManagement.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class DbUtil {
    private final CraftedHorizonRankManagement plugin;
    private static DbUtil instance;
    private static HikariDataSource dataSource;

    private DbUtil(CraftedHorizonRankManagement plugin) {
        this.plugin = plugin;
        setupDataSource();
    }

    private void setupDataSource() {
        HikariConfig config = new HikariConfig();

        String username = plugin.getConfig().getString("database.username");
        String password = plugin.getConfig().getString("database.password");
        String host = plugin.getConfig().getString("database.host");
        String port = plugin.getConfig().getString("database.port");
        String database = plugin.getConfig().getString("database.database");

        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            logError("MariaDB JDBC Driver not found.", e);
            return;
        }

        String url = "jdbc:mariadb://" + host + ":" + port + "/" + database;
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);

        dataSource = new HikariDataSource(config);
    }

    public static synchronized DbUtil getInstance(CraftedHorizonRankManagement plugin) {
        if (instance == null) {
            instance = new DbUtil(plugin);
        }
        return instance;
    }

    public static synchronized Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            instance.setupDataSource();
        }
        return dataSource.getConnection();
    }

    public static void initDatabase() {
        try (Connection conn = getConnection();
             PreparedStatement stmt1 = conn.prepareStatement("CREATE TABLE IF NOT EXISTS mcusers (uuid VARCHAR(36) NOT NULL, userid BIGINT NOT NULL, linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (uuid))");
             PreparedStatement stmt2 = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS linkcodes (" +
                             "uuid VARCHAR(36) NOT NULL, " +
                             "linkcode VARCHAR(36) NOT NULL, " +
                             "expires_at DATETIME NOT NULL, " +
                             "PRIMARY KEY (uuid))");
             PreparedStatement stmt3 = conn.prepareStatement("DROP TRIGGER IF EXISTS set_expiration");
             PreparedStatement stmt4 = conn.prepareStatement(
                     "CREATE TRIGGER set_expiration " +
                             "BEFORE INSERT ON linkcodes " +
                             "FOR EACH ROW " +
                             "SET NEW.expires_at = NOW() + INTERVAL 10 MINUTE")
        ) {
            stmt1.executeUpdate();
            stmt2.executeUpdate();
            stmt3.executeUpdate();
            stmt4.executeUpdate();
        } catch (SQLException e) {
            logError("Error initializing database", e);
        }
    }

    public static synchronized void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public static void executeUpdate(String query, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            setParameters(pstmt, params);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logError("Error executing update", e);
        }
    }

    public static Object executeQuery(String query, String columnName, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
            setParameters(pstmt, params);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.first()) {
                    return rs.getObject(columnName);
                }
            }
        } catch (SQLException e) {
            logError("Error executing query", e);
        }
        return null;
    }

    public static void setParameters(PreparedStatement pstmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param instanceof String) {
                pstmt.setString(i + 1, (String) param);
            } else if (param instanceof Integer) {
                pstmt.setInt(i + 1, (Integer) param);
            } else if (param instanceof Long) {
                pstmt.setLong(i + 1, (Long) param);
            } else if (param instanceof Boolean) {
                pstmt.setBoolean(i + 1, (Boolean) param);
            } else if (param instanceof Timestamp) {
                pstmt.setTimestamp(i + 1, (Timestamp) param);
            } else {
                pstmt.setObject(i + 1, param);
            }
        }
    }

    private static void logError(String message, Exception e) {
        System.err.println(message);
        e.printStackTrace();
    }
}
