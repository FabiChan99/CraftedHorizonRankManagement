package me.fabichan.craftedHorizonRankManagement.util;

import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.*;

public class McUtil {
    private static DbUtil dbclient;
    private static CraftedHorizonRankManagement BukkitPlugin;

    public McUtil(CraftedHorizonRankManagement plugin) {
        dbclient = DbUtil.getInstance(plugin);
        BukkitPlugin = plugin;
    }

    public static String getNameByUUID(UUID uuid) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer.getName();
        } else {
            return "Unknown";
        }
    }

    public static List<OfflinePlayer> getAllPlayersEverPlayedAsOfflinePlayer() {
        List<OfflinePlayer> players = new ArrayList<>();
        OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();

        Collections.addAll(players, allPlayers);

        return players;
    }

    public static String getLastOnline(UUID uuid) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
            Date lastPlayed = new Date(offlinePlayer.getLastPlayed());
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return dateFormat.format(lastPlayed);
        } else {
            return "Unknown Date";
        }
    }

}