package me.fabichan.craftedHorizonRankManagement.util

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement
import org.bukkit.plugin.java.JavaPlugin
import java.sql.*

class DbUtil (private val plugin: JavaPlugin) {
    init {
        setupDataSource()
    }

    private fun setupDataSource() {
        val config = HikariConfig()

        val username = plugin.config.getString("database.username")
        val password = plugin.config.getString("database.password")
        val host = plugin.config.getString("database.host")
        val port = plugin.config.getString("database.port")
        val database = plugin.config.getString("database.database")
        Class.forName("org.mariadb.jdbc.Driver")
        val url = "jdbc:mariadb://$host:$port/$database"

        config.jdbcUrl = url
        config.username = username
        config.password = password
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        config.maximumPoolSize = 10
        config.minimumIdle = 2

        dataSource = HikariDataSource(config)
    }

    companion object {
        private var instance: DbUtil? = null
        private var dataSource: HikariDataSource? = null

        @Synchronized
        fun getInstance(plugin: JavaPlugin): DbUtil {
            if (instance == null) {
                instance = DbUtil(plugin)
            }
            return instance!!
        }

        @get:Throws(SQLException::class)
        @get:Synchronized
        val connection: Connection
            get() {
                if (dataSource == null || dataSource!!.isClosed) {
                    instance!!.setupDataSource()
                }
                return dataSource!!.connection
            }

        fun initDatabase() {
            try {
                connection.use { conn ->
                    conn.createStatement().use { statement ->
                        // Create mcusers table
                        statement.executeUpdate("CREATE TABLE IF NOT EXISTS mcusers (uuid VARCHAR(36) NOT NULL, userid BIGINT NOT NULL, linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (uuid))")

                        // Create linkcodes table
                        statement.executeUpdate(
                            "CREATE TABLE IF NOT EXISTS linkcodes (" +
                                    "uuid VARCHAR(36) NOT NULL, " +
                                    "linkcode VARCHAR(36) NOT NULL, " +
                                    "expires_at DATETIME NOT NULL, " +
                                    "PRIMARY KEY (uuid))"
                        )

                        // Drop the trigger if it exists
                        statement.executeUpdate("DROP TRIGGER IF EXISTS set_expiration")

                        // Create a trigger to automatically set the expiration time
                        statement.executeUpdate(
                            "CREATE TRIGGER set_expiration " +
                                    "BEFORE INSERT ON linkcodes " +
                                    "FOR EACH ROW " +
                                    "SET NEW.expires_at = NOW() + INTERVAL 10 MINUTE"
                        )
                    }
                }
            } catch (e: SQLException) {
                logError("Error initializing database", e)
            }
        }



        @Synchronized
        fun closeDataSource() {
            if (dataSource != null && !dataSource!!.isClosed) {
                dataSource!!.close()
            }
        }

        fun executeUpdate(query: String?, vararg params: Any?) {
            try {
                connection.use { conn ->
                    conn.prepareStatement(query).use { pstmt ->
                        setParameters(pstmt, *params)
                        pstmt.executeUpdate()
                    }
                }
            } catch (e: SQLException) {
                logError("Error executing update", e)
            }
        }

        fun executeQuery(query: String?, columnName: String?, vararg params: Any?): Any? {
            try {
                connection.use { conn ->
                    conn.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)
                        .use { pstmt ->
                            setParameters(pstmt, *params)
                            pstmt.executeQuery().use { rs ->
                                if (rs.first()) {
                                    return rs.getObject(columnName)
                                }
                            }
                        }
                }
            } catch (e: SQLException) {
                logError("Error executing query", e)
            }
            return null
        }

        @Throws(SQLException::class)
        fun setParameters(pstmt: PreparedStatement, vararg params: Any?) {
            for (i in params.indices) {
                if (params[i] is String) {
                    pstmt.setString(i + 1, params[i] as String?)
                } else if (params[i] is Int) {
                    pstmt.setInt(i + 1, (params[i] as Int?)!!)
                } else if (params[i] is Long) {
                    pstmt.setLong(i + 1, (params[i] as Long?)!!)
                } else if (params[i] is Boolean) {
                    pstmt.setBoolean(i + 1, (params[i] as Boolean?)!!)
                } else if (params[i] is Timestamp) {
                    pstmt.setTimestamp(i + 1, params[i] as Timestamp?)
                } else {
                    pstmt.setObject(i + 1, params[i])
                }
            }
        }

        private fun logError(message: String, e: Exception) {
            System.err.println(message)
            e.printStackTrace()
        }
    }
}
