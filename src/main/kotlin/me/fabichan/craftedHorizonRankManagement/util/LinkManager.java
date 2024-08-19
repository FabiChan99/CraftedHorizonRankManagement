package me.fabichan.craftedHorizonRankManagement.util;

import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement;
import net.dv8tion.jda.api.JDA;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static me.fabichan.craftedHorizonRankManagement.util.DbUtil.*;


public class LinkManager {
    private static CraftedHorizonRankManagement BukkitPlugin;

    public LinkManager(CraftedHorizonRankManagement plugin) {
        BukkitPlugin = plugin;
    }

    private static void deleteExpiredCodes(UUID minecraftUuid) {
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement("DELETE FROM linkcodes WHERE uuid = ? AND expires_at <= CURRENT_TIMESTAMP")) {

            pstmt.setString(1, minecraftUuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {

        }
    }
    
    public static String getPendingExpirationDate(String linkCode) {
        if (linkCode == null) {
            return null;
        }

        Object result = executeQuery("SELECT expires_at FROM linkcodes WHERE linkcode = ? AND expires_at > CURRENT_TIMESTAMP", "expires_at", linkCode);
        if (result instanceof Timestamp) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return dateFormat.format((Timestamp) result);
        } else if (result instanceof String) {
            return (String) result;
        } else {
            return "Unknown Date";
        }
    }
    

    public static String getLinkDate(UUID mcuuid) {
        if (mcuuid == null) {
            throw new IllegalArgumentException("UUID must not be null");
        }

        try {
            Object result = executeQuery("SELECT linked_at FROM mcusers WHERE uuid = ?", "linked_at", mcuuid.toString());

            if (result instanceof Timestamp) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                return dateFormat.format((Timestamp) result);
            } else if (result instanceof String) {
                return (String) result;
            } else {
                return "Unknown Date";
            }
        } catch (Exception e) {
            BukkitPlugin.getLogger().severe("Fehler beim Abrufen des Verknüpfungsdatums!" + e.getMessage());
            return "Error retrieving date";
        }
    }


    public static boolean isPending(String linkCode) {
        return checkIfExists("SELECT * FROM linkcodes WHERE linkcode = ? AND expires_at > CURRENT_TIMESTAMP", linkCode);
    }
    public static boolean isPending(UUID minecraftUuid) {
        return checkIfExists("SELECT * FROM linkcodes WHERE uuid = ? AND expires_at > CURRENT_TIMESTAMP", minecraftUuid.toString());
    }
    
    public static String getPendingLinkCode(UUID minecraftUuid) {
        return (String) executeQuery("SELECT linkcode FROM linkcodes WHERE uuid = ? AND expires_at > CURRENT_TIMESTAMP", "linkcode", minecraftUuid.toString());
    }

    public static UUID GetPendingUser(String linkCode) {
        if (linkCode == null) {
            return null;
        }

        Object result = executeQuery("SELECT uuid FROM linkcodes WHERE linkcode = ? AND expires_at > CURRENT_TIMESTAMP", "uuid", linkCode);
        return getUuid(result);
    }

    public static String generateLinkCode(UUID minecraftUuid) {
        String existingCode = checkForExistingCode(minecraftUuid);
        if (existingCode != null) {
            return existingCode;
        }

        deleteExpiredCodes(minecraftUuid);

        String code = "";
        for (int i = 0; i < 8; i++) {
            code += (int) (Math.random() * 10);
        }

        long expiresAt = System.currentTimeMillis() + 600000; // 600000 Millisekunden = 10 Minuten
        executeUpdate("INSERT INTO linkcodes (uuid, linkcode, expires_at) VALUES (?, ?, ?)", minecraftUuid.toString(), code, new Timestamp(expiresAt));

        return code;
    }

    private static String checkForExistingCode(UUID minecraftUuid) {
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement("SELECT linkcode FROM linkcodes WHERE uuid = ? AND expires_at > CURRENT_TIMESTAMP")) {

            pstmt.setString(1, minecraftUuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("linkcode");
                }
            }
        } catch (SQLException e) {
            BukkitPlugin.getLogger().severe("Fehler beim Überprüfen, ob ein Link-Code existiert!");
        }
        return null;
    }

    public static boolean isLinked(long discordId) {
        return checkIfExists("SELECT * FROM mcusers WHERE userid = ?", discordId);
    }

    public static boolean isLinked(UUID minecraftUuid) {
        return checkIfExists("SELECT * FROM mcusers WHERE uuid = ?", minecraftUuid.toString());
    }

    public static void linkAccounts(long discordId, UUID minecraftUuid) {
        executeUpdate("INSERT INTO mcusers (uuid, userid, linked_at) VALUES (?, ?, CURRENT_TIMESTAMP)", minecraftUuid.toString(), discordId);
        executeUpdate("DELETE FROM linkcodes WHERE uuid = ?", minecraftUuid.toString());
    }

    public static boolean isLinkCodeValid(String linkCode) {
        return checkIfExists("SELECT * FROM linkcodes WHERE linkcode = ?", linkCode);
    }

    public static void unlinkAccounts(long discordId) {
        executeUpdate("DELETE FROM mcusers WHERE userid = ?", discordId);
    }

    public static void unlinkAccounts(UUID minecraftUuid) {
        executeUpdate("DELETE FROM mcusers WHERE uuid = ?", minecraftUuid.toString());
    }

    public static UUID getMinecraftUuid(long discordId) {
        Object object = executeQuery("SELECT uuid FROM mcusers WHERE userid = ?", "uuid", discordId);
        return getUuid(object);
    }

    private static UUID getUuid(Object object) {
        if (object == null) {
            return null;
        }
        String dbEntry = object.toString();
        if (dbEntry.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(dbEntry);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String getDiscordId(UUID minecraftUuid) {
        Object result = executeQuery("SELECT userid FROM mcusers WHERE uuid = ?", "userid", minecraftUuid.toString());
        if (result instanceof Long) {
            return Long.toString((Long) result);
        } else if (result instanceof String) {
            return (String) result;
        } else {
            return null;
        }
    }

    public static String getLinkCode(String minecraftUuid) {
        return (String) executeQuery("SELECT linkcode FROM linkcodes WHERE uuid = ?", "linkcode", minecraftUuid);
    }

    public static String GetUUIDByLinkCode(String linkCode) {
        return (String) executeQuery("SELECT uuid FROM linkcodes WHERE linkcode = ?", "uuid", linkCode);
    }

    private static boolean checkIfExists(String query, Object... params) {
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {

            setParameters(pstmt, params);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.first();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void LinkAndInvalidateCode(long discordId, UUID minecraftUuid, String linkCode) {
        linkAccounts(discordId, minecraftUuid);
        executeUpdate("DELETE FROM linkcodes WHERE linkcode = ?", linkCode);
    }
    
    // linker section
    
    // ranks.yml
    /*
    ranks:
  administrator:
    permission: "discordrank.administrator"
    roleId: "123456789"
  moderator:
    permission: "discordrank.moderator"
    roleId: "123456789"
     */
    
    public static List<Long> getAllDiscordsThatAreLinkedAndOnServer() {
        List<Long> discordIds = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement("SELECT userid FROM mcusers WHERE linked_at IS NOT NULL")) {
            JDA jda = JDAProvider.INSTANCE.getJda();
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long discordId = rs.getLong("userid");
                    // null check jda
                    if (jda == null) {
                        throw new IllegalStateException("JDA not initialized");
                    }
                    if (Objects.requireNonNull(jda.getGuildById(Objects.requireNonNull(BukkitPlugin.getConfig().getString("bot.guildid")))).getMemberById(discordId) != null) {
                        discordIds.add(discordId);
                    }

                }
            }
        } catch (SQLException e) {
            BukkitPlugin.getLogger().severe("Fehler beim Abrufen der verknüpften Discord-IDs!");
        }
        return discordIds;
    }
    
    // ranksync task
    
    // implement ranksync task here that checks if the user has the correct role and if not
    // also check if the user is linked and on the server
    // also remove the role if the user is not linked anymore
    // also remove the the role if the user doesnt have the correct role anymore
    

}