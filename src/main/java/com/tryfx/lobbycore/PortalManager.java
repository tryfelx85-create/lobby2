package com.tryfx.lobbycore;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.LookClose;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spawns two villager NPCs in the lobby that act as portals: right-clicking
 * one teleports the player to the SMP world, the other to the Training world.
 * Destination worlds are resolved by name at click-time (not cached at
 * startup) so this still works correctly even if those plugins/worlds load
 * after LobbyCore.
 */
public final class PortalManager implements Listener {

    public enum PortalDestination { SMP, TRAINING }

    private final LobbyCore plugin;
    private NPCRegistry npcRegistry;
    private final Map<UUID, PortalDestination> portalNpcs = new HashMap<>();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Configurable world names for destinations. The SMP world name should
    // match whatever the main survival world is actually called on this
    // server (commonly "world" for the default Paper world) - adjust in
    // config.yml if different.
    private String smpWorldName;
    private String trainingWorldName;

    public PortalManager(LobbyCore plugin) {
        this.plugin = plugin;
        this.smpWorldName = plugin.getConfig().getString("worlds.smp-world-name", "world");
        this.trainingWorldName = plugin.getConfig().getString("worlds.training-world-name", "tryfx_training");

        // Use Citizens' own default registry rather than a custom named one
        // - createNamedNPCRegistry(name, dataStore) needs a real
        // NPCDataStore as its second argument. Passing null (as this code
        // previously did) left Citizens' internal "saves" reference null,
        // which crashes with a NullPointerException the moment an NPC is
        // created. getNPCRegistry() is already correctly backed by
        // Citizens' own saves.yml. See TryFXTheBot's BotManager.start() for
        // the fuller explanation - same bug, same fix, both plugins.
        this.npcRegistry = CitizensAPI.getNPCRegistry();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Spawns (or re-teleports, if already spawned from a previous run) a
     * portal villager at the given location, bound to the given destination.
     */
    public NPC createOrUpdatePortal(PortalDestination destination, Location location, String displayName) {
        // Avoid duplicate spawns across restarts by checking existing NPCs
        for (NPC existing : npcRegistry) {
            PortalDestination existingDest = portalNpcs.get(existing.getUniqueId());
            if (existingDest == destination) {
                existing.teleport(location, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                return existing;
            }
        }

        NPC npc = npcRegistry.createNPC(EntityType.VILLAGER, displayName);
        npc.spawn(location);
        npc.getNavigator().setPaused(true); // portal NPCs should not wander

        // Eye contact: the villager turns to face the nearest player within
        // range, rather than standing fixed in its spawn orientation.
        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        lookClose.lookClose(true);
        lookClose.setRange(8.0);

        portalNpcs.put(npc.getUniqueId(), destination);
        return npc;
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        NPC clickedNpc = event.getNPC();
        if (clickedNpc == null) return;

        PortalDestination destination = portalNpcs.get(clickedNpc.getUniqueId());
        if (destination == null) return; // not one of our portal NPCs

        Player player = event.getClicker();
        teleportToDestination(player, destination);
    }

    public void teleportToDestination(Player player, PortalDestination destination) {
        String worldName = destination == PortalDestination.SMP ? smpWorldName : trainingWorldName;
        var world = Bukkit.getWorld(worldName);

        if (world == null) {
            player.sendMessage(MM.deserialize("<red>That destination is not currently available."));
            plugin.getLogger().warning("Portal destination world '" + worldName + "' not found/loaded.");
            return;
        }

        Location dest = destination == PortalDestination.TRAINING
                ? world.getSpawnLocation()
                : world.getSpawnLocation();

        player.teleport(dest);
        String label = destination == PortalDestination.SMP ? "SMP" : "Training Arena";
        player.sendMessage(MM.deserialize("<green>Teleported to " + label + "."));
    }
}
