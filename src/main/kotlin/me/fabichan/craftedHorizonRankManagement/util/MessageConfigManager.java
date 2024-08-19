package me.fabichan.agcminetools.Utils;

import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Level;

public class MessageConfigManager {

    private static CustomConfigManager configManager;
    private static JavaPlugin plugin;

    public MessageConfigManager(JavaPlugin plugin) {
        MessageConfigManager.plugin = plugin;
        configManager = new CustomConfigManager(plugin, "messages.yml");
        System.out.println("Loading messages.yml");
        configManager.saveDefaultConfig();
    }

    public static String getFallbackFromEmbeddedResource(String section) {
        if (plugin == null) {
            throw new IllegalStateException("Plugin reference not set in MessageConfigManager");
        }

        try (InputStream inputStream = plugin.getResource("messages.yml")) {
            if (inputStream == null) {
                plugin.getLogger().log(Level.WARNING, "Embedded resource 'messages.yml' not found.");
                return "";
            }
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);
            if (data.containsKey(section)) {
                return data.get(section).toString();
            } else {
                return "";
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error reading from embedded resource.", e);
            return "";
        }
    }

    public static String getMessage(String section) {
        if (configManager == null) {
            throw new IllegalStateException("ConfigManager not initialized in MessageConfigManager");
        }

        String message = configManager.getConfig().getString(section);
        if (message == null) {
            message = getFallbackFromEmbeddedResource(section);
        }
        return message;
    }
}
