package me.fabichan.craftedHorizonRankManagement.commands.minecraft

import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement
import me.fabichan.craftedHorizonRankManagement.util.LinkManager
import me.fabichan.craftedHorizonRankManagement.util.RankSyncTask.Companion.syncRoles
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.util.*

class UserJoinSync(private val plugin: CraftedHorizonRankManagement): ListenerAdapter() {

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        val discordId = event.user.idLong
        val linked: Boolean = LinkManager.isLinked(discordId)
        if (linked) {
            plugin.server.scheduler.runTaskAsynchronously(plugin,
                Runnable {
                    syncRoles(Objects.requireNonNull(event.member))
                })
        }
    }
}