package me.fabichan.craftedHorizonRankManagement.commands.discord

import me.fabichan.craftedHorizonRankManagement.util.Interfaces.ICommand
import me.fabichan.craftedHorizonRankManagement.util.MessageConfigManager
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.components.buttons.Button


class SendRegisterModal : ICommand {
    override fun getName(): String {
        return "sendregistermodal"
    }

    override fun getDescription(): String {
        return "Sende den Registrierungs-Modal in den aktuellen Channel"
    }

    override fun handle(event: SlashCommandInteractionEvent) {
        if (!hasRequiredPermissions(event)) {
            event.reply("You do not have the required permissions to use this command.").setEphemeral(true).queue()
            return
        }
        event.deferReply().queue()
        val embed = EmbedBuilder()
        val registerEmbedMessage = MessageConfigManager.getMessage("discord.registerEmbedMessage")
        embed.setDescription(registerEmbedMessage)
        embed.setColor(0x00ff00)

        val button = Button.primary("mcregister", "Minecraft verknüpfen")
        event.channel.sendMessageEmbeds(embed.build()).setActionRow(button).queue()
    }

    override fun getRequiredPermissions(): List<Permission> {
        return listOf(Permission.ADMINISTRATOR)
    }

    override fun getCommandData(): CommandData {
        return Commands.slash(name, description)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(requiredPermissions))
    }
}