package com.serverside.anticheat.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.serverside.anticheat.ServerSideAntiCheat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.player.PlayerQuitEvent;

public class TranslationProbeTask implements Listener {

    private final ServerSideAntiCheat plugin;
    private final ProtocolManager protocolManager;
    private final Map<UUID, Map<String, String>> expectedRawKeys = new ConcurrentHashMap<>(); // uuid -> (prefix -> expected text)
    private final Map<UUID, Map<String, String>> prefixToMod = new ConcurrentHashMap<>(); // uuid -> (prefix -> modId)

    private TranslationProbeTask(ServerSideAntiCheat plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();

        final ServerSideAntiCheat pluginRef = plugin;
        
        // Listen for the ITEM_NAME packet. When a player has an Anvil open, 
        // the client automatically populates the text box with the item's display name
        // and sends this packet to the server.
        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Client.ITEM_NAME) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                try {
                    String text = event.getPacket().getStrings().read(0);
                    UUID uuid = event.getPlayer().getUniqueId();
                    
                    if (!expectedRawKeys.containsKey(uuid)) return;
                    
                    // The text should start with our 1-character prefix
                    if (text.length() >= 1) {
                        String prefix = text.substring(0, 1);
                        Map<String, String> rawMap = expectedRawKeys.get(uuid);
                        Map<String, String> modMap = prefixToMod.get(uuid);
                        
                        if (rawMap != null && rawMap.containsKey(prefix)) {
                            String expected = rawMap.get(prefix);
                            String modId = modMap.get(prefix);
                            
                
                            if (modId.equals("TERMINATOR")) {
                                // We reached the end of the probe queue!
                                rawMap.remove(prefix);
                                modMap.remove(prefix);
                                
                                if (!rawMap.isEmpty()) {
                                    // If there are ANY keys left in the map, it means the client silently dropped 
                                    // the ITEM_NAME packet for them! (Usually because the translation contained a newline \n).
                                    // This proves they have the mod installed!
                                    String firstMissing = rawMap.keySet().iterator().next();
                                    String bannedMod = modMap.get(firstMissing);
                                    
                                    expectedRawKeys.remove(uuid);
                                    prefixToMod.remove(uuid);
                                    
                                    Bukkit.getScheduler().runTask(pluginRef, () -> {
                                        String kickMsg = pluginRef.getKickMessage().replace("%reason%", bannedMod + " is banned");
                                        event.getPlayer().kickPlayer(ChatColor.translateAlternateColorCodes('&', kickMsg));
                                    });
                                }
                                return;
                            }
                            
                            if (!text.equals(expected)) {
                                // The text does not match the raw key, meaning the client translated it
                                expectedRawKeys.remove(uuid);
                                prefixToMod.remove(uuid); // Stop probing this player
                                
                                Bukkit.getScheduler().runTask(pluginRef, () -> {
                                    String kickMsg = pluginRef.getKickMessage().replace("%reason%", modId + " is banned");
                                    event.getPlayer().kickPlayer(ChatColor.translateAlternateColorCodes('&', kickMsg));
                                });
                            } else {
                                // Clean up this specific probe
                                rawMap.remove(prefix);
                                modMap.remove(prefix);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        });
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static void init(ServerSideAntiCheat plugin) {
        new TranslationProbeTask(plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        expectedRawKeys.remove(event.getPlayer().getUniqueId());
        prefixToMod.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<String> keys = new ArrayList<>(plugin.getResolvedTranslationKeys().keySet());
        
        if (keys.isEmpty()) return;
        
        // Add a guaranteed dummy terminator key to the end of the queue.
        // Since no mod will ever translate this, it will ALWAYS return successfully.
        // When we receive this, we know the client has finished processing all our invisible Anvils!
        keys.add("anticheat.terminator.packet.end");

        Map<String, String> rawMap = new ConcurrentHashMap<>();
        Map<String, String> modMap = new ConcurrentHashMap<>();
        expectedRawKeys.put(player.getUniqueId(), rawMap);
        prefixToMod.put(player.getUniqueId(), modMap);

        // Delay to ensure the client is fully connected before probing
        new BukkitRunnable() {
            int keyIndex = 0;

            @Override
            public void run() {
                if (!player.isOnline() || keyIndex >= keys.size()) {
                    this.cancel();
                    return;
                }

                String rawKey = keys.get(keyIndex);
                String modId;
                if (keyIndex == keys.size() - 1) {
                    modId = "TERMINATOR";
                } else {
                    modId = plugin.getResolvedTranslationKeys().get(rawKey);
                }
                
                // Use a single character prefix to save space (Anvil max length is 50)
                String prefix = String.valueOf((char) (33 + keyIndex));
                String expectedText = prefix + rawKey;
                
                // The Anvil text box has a hardcoded limit of 50 characters on the client.
                // If our expected string is longer, the client will truncate it before sending it back.
                // We truncate our expectation here to perfectly match what the client will send.
                if (expectedText.length() > 50) {
                    expectedText = expectedText.substring(0, 50);
                }
                
                rawMap.put(prefix, expectedText);
                modMap.put(prefix, modId);
                
                // Open Anvil
                Inventory anvil = Bukkit.createInventory(player, org.bukkit.event.inventory.InventoryType.ANVIL, Component.text("Checking..."));
                
                // Create probe item with translation key
                ItemStack probeItem = new ItemStack(Material.NAME_TAG);
                ItemMeta meta = probeItem.getItemMeta();
                if (meta != null) {
                    // Prepend the 3-character prefix to the translation component so it gets sent back to us
                    meta.displayName(Component.text(prefix).append(Component.translatable(rawKey)));
                    probeItem.setItemMeta(meta);
                }
                
                anvil.setItem(0, probeItem);
                
                // Force open the inventory
                player.openInventory(anvil);
                
                // Close it INSTANTLY in the same tick. 
                // The client will process OPEN_WINDOW -> WINDOW_ITEMS -> CLOSE_WINDOW
                // sequentially before the next frame renders, making the Anvil 100% invisible!
                // It still sends the ITEM_NAME packet back during the WINDOW_ITEMS processing.
                player.closeInventory();

                keyIndex++;
            }
        }.runTaskTimer(plugin, 1L, 2L); // Start instantly (during Loading Terrain screen) to avoid interrupting movement
    }
}
