package me.fabichan.agcminetools.Utils;

import me.fabichan.agcminetools.Utils.Interfaces.ICommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.*;

public class CommandManager extends ListenerAdapter {
    private final Map<String, ICommand> commands = new HashMap<>();

    public Set<ICommand> getCommands() {
        return Collections.unmodifiableSet(new HashSet<>(commands.values()));
    }

    public void addCommand(ICommand command) {
        commands.put(command.getName(), command);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        ICommand command = commands.get(event.getName());

        if (command != null) {
            if (!command.hasRequiredPermissions(event)) {
                event.reply("Du hast nicht die erforderlichen Berechtigungen für diesen Befehl.").setEphemeral(true).queue();
                return;
            }
            command.handle(event);
        }
    }
}