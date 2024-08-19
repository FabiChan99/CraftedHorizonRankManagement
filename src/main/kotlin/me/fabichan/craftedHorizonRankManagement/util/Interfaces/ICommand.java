package me.fabichan.craftedHorizonRankManagement.util.Interfaces

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData

interface ICommand {
    val name: String?

    val description: String?

    fun handle(event: SlashCommandInteractionEvent?)

    val commandData: CommandData?

    val requiredPermissions: List<Permission?>

    fun hasRequiredPermissions(event: SlashCommandInteractionEvent): Boolean {
        if (requiredPermissions.isEmpty()) {
            return true
        }
        return event.member?.hasPermission(
            requiredPermissions
        ) ?: false
    }
}