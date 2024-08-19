package me.fabichan.craftedHorizonRankManagement.util

import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import java.text.SimpleDateFormat
import java.util.*

class McUtil(private var BukkitPlugin: CraftedHorizonRankManagement) {
    init {
        dbclient = DbUtil.getInstance(BukkitPlugin)
    }

    companion object {
        private lateinit var dbclient: DbUtil

        fun getNameByUUID(uuid: UUID): String? {
            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
            return if (offlinePlayer.hasPlayedBefore()) {
                offlinePlayer.name
            } else {
                "Unknown"
            }
        }

        val allPlayersEverPlayedAsOfflinePlayer: List<OfflinePlayer>
            get() {
                val players: List<OfflinePlayer> = ArrayList()
                val allPlayers = Bukkit.getOfflinePlayers()

                for (player in allPlayers) {
                    if (player.hasPlayedBefore()) {
                        (players as ArrayList).add(player)
                    }
                }

                return players
            }

        fun getLastOnline(uuid: UUID): String {
            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
            if (offlinePlayer.hasPlayedBefore()) {
                val lastPlayed = Date(offlinePlayer.lastPlayed)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                return dateFormat.format(lastPlayed)
            } else {
                return "Unknown Date"
            }
        }
    }
}