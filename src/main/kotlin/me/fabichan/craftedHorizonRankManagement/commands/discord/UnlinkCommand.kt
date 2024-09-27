package me.fabichan.craftedHorizonRankManagement.commands.discord

import me.fabichan.craftedHorizonRankManagement.util.Interfaces.ICommand
import me.fabichan.craftedHorizonRankManagement.util.LinkManager
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import java.util.*


class UnlinkCommand : ICommand {
    override fun getName(): String {
        return "unlink"
    }

    override fun getDescription(): String {
        return "Entferne die Verlinkung zu einem Minecraft-Account"
    }

    override fun handle(event: SlashCommandInteractionEvent) {
        val user = event.getOption("user")?.asUser
        val isL = LinkManager.isLinked(user!!.idLong)
        if (!isL) {
            event.reply("Der Benutzer ist nicht verlinkt!").setEphemeral(true).queue()
            return
        }
        LinkManager.unlinkAccounts(user.idLong)
        event.reply("Der Benutzer wurde erfolgreich entlinkt!").setEphemeral(true).queue()
    }

    override fun getRequiredPermissions(): List<Permission> {
        return listOf(Permission.ADMINISTRATOR)
    }

    override fun getCommandData(): CommandData {
        return Commands.slash(name, description)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(requiredPermissions)).addOption(OptionType.USER, "user", "Der Benutzer, der entlinkt werden soll", true)
    }
}