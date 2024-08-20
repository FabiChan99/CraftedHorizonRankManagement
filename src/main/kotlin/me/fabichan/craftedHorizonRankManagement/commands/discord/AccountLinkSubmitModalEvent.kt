package me.fabichan.craftedHorizonRankManagement.commands.discord

import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement
import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement.properties
import me.fabichan.craftedHorizonRankManagement.CraftedHorizonRankManagement.properties.ChatPrefix
import me.fabichan.craftedHorizonRankManagement.util.LinkManager
import me.fabichan.craftedHorizonRankManagement.util.McUtil
import me.fabichan.craftedHorizonRankManagement.util.RankSyncTask.Companion.syncRoles
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.apache.commons.lang3.Validate
import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin
import java.util.*


class AccountLinkSubmitModalEvent(private val plugin: CraftedHorizonRankManagement) : ListenerAdapter() {
    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (event.modalId == "linkmodal") {
            val inputs = event.values
            if (!inputs.isEmpty()) {
                val linkCode = inputs[0].asString
                if (LinkManager.isLinkCodeValid(linkCode)) {
                    val discordId = event.user.idLong
                    val minecraftUuid = LinkManager.GetPendingUser(linkCode)
                    if (minecraftUuid == null) {
                        event.reply("Der Link-Code ist ungültig!").setEphemeral(true).queue()
                        return
                    }
                    LinkManager.LinkAndInvalidateCode(discordId, minecraftUuid, linkCode)
                    val minecraftName = McUtil.getNameByUUID(minecraftUuid)
                    val message = String.format(
                        "Dein Discord-Account wurde mit dem Minecraft-Account `%s` verknüpft!",
                        minecraftName
                    )
                    event.reply(message).setEphemeral(true).queue()
                    // as bukkitrunnable
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        event.member?.let { syncRoles(it) }
                        val player = plugin.server.getPlayer(minecraftUuid)
                        if (player != null) {
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                player.sendMessage("${ChatPrefix}${ChatColor.GREEN}Dein Minecraft-Account wurde mit ${ChatColor.AQUA}${event.user.name}${ChatColor.GREEN} verknüpft!")
                            })
                        }
                    })
                } else {
                    event.reply("Der Link-Code ist ungültig!").setEphemeral(true).queue()
                }
            }
        }
    }
}