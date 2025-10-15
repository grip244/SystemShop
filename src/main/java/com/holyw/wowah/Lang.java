package com.holyw.wowah;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class Lang {

    private static FileConfiguration langConfig;

    public static void load(JavaPlugin plugin) {
        File langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false);
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        // Add missing keys from the default lang.yml
        try (InputStreamReader reader = new InputStreamReader(plugin.getResource("lang.yml"))) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
            for (String key : defaultConfig.getKeys(true)) {
                if (!langConfig.contains(key)) {
                    langConfig.set(key, defaultConfig.get(key));
                }
            }
            langConfig.save(langFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String get(String path) {
        String message = langConfig.getString(path, path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String get(String path, String... replacements) {
        String message = get(path);
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }
}