package me.fabichan.agcminetools.Eventlistener;

import me.fabichan.agcminetools.Utils.LinkManager;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

import static me.fabichan.agcminetools.Utils.LinkManager.LinkAndInvalidateCode;
import static me.fabichan.agcminetools.Utils.McUtil.getNameByUUID;

public class AccountLinkSubmitModalEvent extends ListenerAdapter {

    private final JavaPlugin plugin;

    public AccountLinkSubmitModalEvent(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().equals("linkmodal")) {
            List<ModalMapping> inputs = event.getValues();
            if (!inputs.isEmpty()) {
                String linkCode = inputs.get(0).getAsString();
                if (LinkManager.isLinkCodeValid(linkCode)) {
                    long discordId = event.getUser().getIdLong();
                    UUID minecraftUuid = LinkManager.GetPendingUser(linkCode);
                    if (minecraftUuid == null) {
                        event.reply("Der Link-Code ist ungültig!").setEphemeral(true).queue();
                        return;
                    }
                    LinkAndInvalidateCode(discordId, minecraftUuid, linkCode);
                    String minecraftName = getNameByUUID(minecraftUuid);
                    // format message
                    String message = String.format("Dein Discord-Account wurde mit dem Minecraft-Account `%s` verknüpft!", minecraftName);
                    event.reply(message).setEphemeral(true).queue();
                } else {
                    event.reply("Der Link-Code ist ungültig!").setEphemeral(true).queue();
                }
            }

        }
    }

}