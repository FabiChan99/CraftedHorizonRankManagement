package me.fabichan.craftedHorizonRankManagement.commands.minecraft;

import me.fabichan.craftedHorizonRankManagement.util.JDAProvider;
import me.fabichan.craftedHorizonRankManagement.util.LinkManager;
import me.fabichan.craftedHorizonRankManagement.util.RankSyncTask;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import me.fabichan.craftedHorizonRankManagement.util.RankSyncTask.Companion.*;

import java.util.Objects;
import java.util.UUID;

import static me.fabichan.craftedHorizonRankManagement.util.RankSyncTask.syncRoles;

public class MinecraftPlayerJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final JDA jda;

    public MinecraftPlayerJoinListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.jda = JDAProvider.INSTANCE.getJda();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (jda == null) {
                    plugin.getLogger().severe("JDA ist nicht initialisiert!");
                    return;
                }
                Player player = event.getPlayer();
                UUID uuid = player.getUniqueId();
                String discordId = LinkManager.getDiscordId(uuid);
                if (discordId == null) {
                    return;
                }
                Member member = Objects.requireNonNull(jda.getGuildById(Objects.requireNonNull(plugin.getConfig().getString("bot.guildid")))).getMemberById(discordId);
                // run sync 
                if (member == null) {
                    return;
                }
                plugin.getLogger().info("Syncing roles for " + member.getUser().getAsTag());
                syncRoles(member);
            }
        }.runTaskAsynchronously(plugin);
    }
}