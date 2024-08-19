package me.fabichan.craftedHorizonRankManagement.util

import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement
import me.fabichan.craftedHorizonRankManagement.util.DbUtil.Companion.executeQuery
import me.fabichan.craftedHorizonRankManagement.util.DbUtil.Companion.executeUpdate
import java.sql.SQLException
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class LinkManager(private val plugin: CraftedHorizonRankManagement) {
    init {
        dbclient = DbUtil.getInstance(plugin)
    }

    companion object {
        private lateinit var dbclient: DbUtil
        private lateinit var pluginInstance: CraftedHorizonRankManagement

        fun initialize(plugin: CraftedHorizonRankManagement) {
            pluginInstance = plugin
            dbclient = DbUtil.getInstance(plugin)
        }

        private fun deleteExpiredCodes(minecraftUuid: UUID) {
            try {
                executeUpdate("DELETE FROM linkcodes WHERE uuid = ? AND expires_at <= CURRENT_TIMESTAMP", minecraftUuid.toString())
            } catch (e: SQLException) {
                pluginInstance.logger.severe("Error deleting expired codes: ${e.message}")
            }
        }

        fun getLinkDate(mcuuid: UUID): String {
            requireNotNull(mcuuid) { "UUID must not be null" }

            return try {
                val result: Any? = executeQuery("SELECT linked_at FROM mcusers WHERE uuid = ?", "linked_at", mcuuid.toString())
                when (result) {
                    is Timestamp -> {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        dateFormat.format(result)
                    }
                    is String -> result
                    else -> "Unknown Date"
                }
            } catch (e: Exception) {
                pluginInstance.logger.severe("Error retrieving link date: ${e.message}")
                "Error retrieving date"
            }
        }

        fun isPending(linkCode: String?): Boolean {
            return checkIfExists(
                "SELECT * FROM linkcodes WHERE linkcode = ? AND expires_at > CURRENT_TIMESTAMP",
                linkCode!!
            )
        }

        fun getPendingUser(linkCode: String?): UUID? {
            if (linkCode == null) return null

            val result: Any? = executeQuery(
                "SELECT uuid FROM linkcodes WHERE linkcode = ? AND expires_at > CURRENT_TIMESTAMP",
                "uuid",
                linkCode
            )
            return getUuid(result)
        }

        fun generateLinkCode(minecraftUuid: UUID): String {
            val existingCode = checkForExistingCode(minecraftUuid)
            if (existingCode != null) {
                return existingCode
            }

            deleteExpiredCodes(minecraftUuid)

            val code = (1..8).map { (Math.random() * 10).toInt() }.joinToString("")
            val expiresAt = System.currentTimeMillis() + 600000 // 600000 Millisekunden = 10 Minuten

            executeUpdate(
                "INSERT INTO linkcodes (uuid, linkcode, expires_at) VALUES (?, ?, ?)",
                minecraftUuid.toString(),
                code,
                Timestamp(expiresAt)
            )

            return code
        }

        private fun checkForExistingCode(minecraftUuid: UUID): String? {
            return try {
                val result: Any? = executeQuery(
                    "SELECT linkcode FROM linkcodes WHERE uuid = ? AND expires_at > CURRENT_TIMESTAMP",
                    "linkcode",
                    minecraftUuid.toString()
                )
                result as? String
            } catch (e: SQLException) {
                pluginInstance.logger.severe("Error checking for existing code: ${e.message}")
                null
            }
        }

        fun isLinked(discordId: Long): Boolean {
            return checkIfExists("SELECT * FROM mcusers WHERE userid = ?", discordId)
        }

        fun isLinked(minecraftUuid: UUID): Boolean {
            return checkIfExists("SELECT * FROM mcusers WHERE uuid = ?", minecraftUuid.toString())
        }

        fun linkAccounts(discordId: Long, minecraftUuid: UUID) {
            executeUpdate(
                "INSERT INTO mcusers (uuid, userid, linked_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                minecraftUuid.toString(),
                discordId
            )
            executeUpdate("DELETE FROM linkcodes WHERE uuid = ?", minecraftUuid.toString())
        }

        fun isLinkCodeValid(linkCode: String?): Boolean {
            return checkIfExists("SELECT * FROM linkcodes WHERE linkcode = ?", linkCode!!)
        }

        fun unlinkAccounts(discordId: Long) {
            executeUpdate("DELETE FROM mcusers WHERE userid = ?", discordId)
        }

        fun unlinkAccounts(minecraftUuid: UUID) {
            executeUpdate("DELETE FROM mcusers WHERE uuid = ?", minecraftUuid.toString())
        }

        fun getMinecraftUuid(discordId: Long): UUID? {
            val result: Any? = executeQuery("SELECT uuid FROM mcusers WHERE userid = ?", "uuid", discordId)
            return getUuid(result)
        }

        private fun getUuid(result: Any?): UUID? {
            return result?.toString()?.let {
                try {
                    UUID.fromString(it)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }

        fun getDiscordId(minecraftUuid: UUID): String? {
            val result: Any? = executeQuery("SELECT userid FROM mcusers WHERE uuid = ?", "userid", minecraftUuid.toString())
            return result?.toString()
        }

        fun getLinkCode(minecraftUuid: String?): String {
            return executeQuery("SELECT linkcode FROM linkcodes WHERE uuid = ?", "linkcode", minecraftUuid) as String
        }

        fun getUUIDByLinkCode(linkCode: String?): String {
            return executeQuery("SELECT uuid FROM linkcodes WHERE linkcode = ?", "uuid", linkCode) as String
        }

        private fun checkIfExists(query: String, vararg params: Any): Boolean {
            return try {
                val result: Any? = executeQuery(query, "1", *params)
                result != null
            } catch (e: SQLException) {
                pluginInstance.logger.severe("Error checking existence: ${e.message}")
                false
            }
        }

        fun linkAndInvalidateCode(discordId: Long, minecraftUuid: UUID, linkCode: String?) {
            linkAccounts(discordId, minecraftUuid)
            executeUpdate("DELETE FROM linkcodes WHERE linkcode = ?", linkCode)
        }
    }
}
