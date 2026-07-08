package com.serverside.anticheat;

import com.serverside.anticheat.listeners.NetworkListener;
import com.serverside.anticheat.util.ModrinthFetcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerSideAntiCheat extends JavaPlugin {

    private List<String> bannedBrands = new ArrayList<>();
    private List<String> bannedChannels = new ArrayList<>();
    private List<String> bannedModIds = new ArrayList<>();
    private Map<String, String> resolvedTranslationKeys = new HashMap<>();
    
    private String kickMessage;

    private File cacheFile;
    private FileConfiguration cacheConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        loadCache();

        // Print ASCII Art
        MiniMessage mm = MiniMessage.miniMessage();
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        String[] asciiArt = {
                "",
                "<gradient:red:dark_red>▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄</gradient>",
                "<gradient:red:dark_red>█</gradient>                                    <dark_red>█</dark_red>",
                "<gradient:red:dark_red>█</gradient> <bold><gradient:yellow:red>⚡</gradient>  <gradient:gold:yellow>ANTICHEATS</gradient> <dark_gray>✖</dark_gray> <gray>by</gray> <dark_gray>✖</dark_gray> <gradient:aqua:blue>ArcLgenD</gradient>  <gradient:red:yellow>⚡</gradient></bold> <dark_red>█</dark_red>",
                "<gradient:red:dark_red>█</gradient>                                    <dark_red>█</dark_red>",
                "<gradient:red:dark_red>▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀</gradient>",
                "",
                "<gray>Version:</gray> <bold><white>" + getDescription().getVersion() + "</white></bold> <dark_gray>|</dark_gray> <gray>Status:</gray> <bold><green>ACTIVE (SERVER-SIDE)</green></bold>",
                "<gray>Security:</gray> <bold><gradient:red:dark_red>MAXIMUM ENFORCEMENT</gradient></bold>",
                ""
        };

        for (String line : asciiArt) {
            Component component = mm.deserialize(line);
            getServer().getConsoleSender().sendMessage(legacy.serialize(component));
        }

        // Register Channel Listeners
        NetworkListener networkListener = new NetworkListener(this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "minecraft:brand", networkListener);
        getServer().getPluginManager().registerEvents(networkListener, this);

        // Run Auto-Fetcher async
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (cacheConfig.contains("resolved")) {
                List<String> toRemove = new ArrayList<>();
                for (String modId : cacheConfig.getConfigurationSection("resolved").getKeys(false)) {
                    if (!bannedModIds.contains(modId)) {
                        toRemove.add(modId);
                        continue;
                    }

                    String key = cacheConfig.getString("resolved." + modId);
                    if (resolvedTranslationKeys.containsKey(key)) {
                        String existing = resolvedTranslationKeys.get(key);
                        if (existing.contains(modId)) {
                            resolvedTranslationKeys.put(key, modId);
                        } else if (!modId.contains(existing)) {
                            resolvedTranslationKeys.put(key, existing + " or " + modId);
                        }
                    } else {
                        resolvedTranslationKeys.put(key, modId);
                    }
                }
                
                // Clean up orphaned cache entries
                if (!toRemove.isEmpty()) {
                    for (String orphanedMod : toRemove) {
                        cacheConfig.set("resolved." + orphanedMod, null);
                    }
                    saveCache();
                }
            }

            for (String modId : bannedModIds) {
                if (cacheConfig.contains("resolved." + modId)) {
                    continue;
                }

                String resolvedKey = ModrinthFetcher.fetchTranslationKey(modId);
                if (resolvedKey != null) {
                    if (resolvedTranslationKeys.containsKey(resolvedKey)) {
                        String existing = resolvedTranslationKeys.get(resolvedKey);
                        if (existing.contains(modId)) {
                            resolvedTranslationKeys.put(resolvedKey, modId);
                        } else if (!modId.contains(existing)) {
                            resolvedTranslationKeys.put(resolvedKey, existing + " or " + modId);
                        }
                    } else {
                        resolvedTranslationKeys.put(resolvedKey, modId);
                    }
                    cacheConfig.set("resolved." + modId, resolvedKey);
                    saveCache();
                } else {
                    getLogger().warning("Failed to resolve translation key for " + modId + ". Is the mod ID correct?");
                }
            }
            
            // Fetches completed
        });

        // Start Translation Probe Task immediately
        com.serverside.anticheat.listeners.TranslationProbeTask.init(this);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        kickMessage = config.getString("kick-message", "&c[AntiCheat] &fUnapproved client modification detected: &e%reason%");
        bannedBrands = config.getStringList("banned-brands");
        bannedChannels = config.getStringList("banned-channels");
        bannedModIds = config.getStringList("banned-mod-ids");
    }

    private void loadCache() {
        cacheFile = new File(getDataFolder(), "cache.yml");
        if (!cacheFile.exists()) {
            try {
                cacheFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create cache file!");
            }
        }
        cacheConfig = YamlConfiguration.loadConfiguration(cacheFile);
    }

    private void saveCache() {
        try {
            cacheConfig.save(cacheFile);
        } catch (IOException e) {
            getLogger().severe("Could not save cache file!");
        }
    }

    public List<String> getBannedBrands() { return bannedBrands; }
    public List<String> getBannedChannels() { return bannedChannels; }
    public Map<String, String> getResolvedTranslationKeys() { return resolvedTranslationKeys; }
    public String getKickMessage() { return kickMessage; }
}




