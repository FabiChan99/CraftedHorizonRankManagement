package me.fabichan.agcminetools.Utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class CustomConfigManager {

    private final JavaPlugin plugin;
    private final String configName;
    private FileConfiguration customConfig = null;
    private File customConfigFile = null;

    public CustomConfigManager(JavaPlugin plugin, String configName) {
        this.plugin = plugin;
        this.configName = configName;
    }

    public void reloadConfig() {
        if (customConfigFile == null) {
            customConfigFile = new File(plugin.getDataFolder(), configName);
        }
        customConfig = YamlConfiguration.loadConfiguration(customConfigFile);

        // Look for defaults in the jar
        InputStream defConfigStream = plugin.getResource(configName);
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            customConfig.setDefaults(defConfig);
        }
    }

    public FileConfiguration getConfig() {
        if (customConfig == null) {
            reloadConfig();
        }
        return customConfig;
    }

    public void saveConfig() {
        if (customConfig == null || customConfigFile == null) {
            return;
        }
        try {
            getConfig().save(customConfigFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + customConfigFile, e);
        }
    }

    public void saveDefaultConfig() {
        if (customConfigFile == null) {
            customConfigFile = new File(plugin.getDataFolder(), configName);
        }
        if (!customConfigFile.exists()) {
            plugin.saveResource(configName, false);
        }
    }
}
