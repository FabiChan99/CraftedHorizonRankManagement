package me.fabichan.craftedHorizonRankManagement.util.Interfaces;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;
import java.util.Objects;

public interface ICommand {
    String getName();

    String getDescription();

    void handle(SlashCommandInteractionEvent event);

    CommandData getCommandData();

    List<Permission> getRequiredPermissions();

    default boolean hasRequiredPermissions(SlashCommandInteractionEvent event) {
        if (getRequiredPermissions().isEmpty()) {
            return true;
        }
        return Objects.requireNonNull(event.getMember()).hasPermission(getRequiredPermissions());
    }
}