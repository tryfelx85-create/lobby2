package com.tryfx.lobbycore;

import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks total playtime per player, persisted via each player's
 * PersistentDataContainer (survives restarts automatically as part of
 * vanilla playerdata). Session time accrues in memory and flushes to PDC
 * periodically and on quit.
 */
public final class PlaytimeManager {

    private final LobbyCore plugin;
    private final NamespacedKey playtimeKey;

    // Cached total playtime in ticks, keyed by UUID - includes both
    // previously-persisted time and the current session's elapsed time.
    private final Map<UUID, Long> cachedTotalTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStartTick = new ConcurrentHashMap<>();
    private final Map<UUID, String> knownNames = new ConcurrentHashMap<>();

    private long serverTickCounter = 0;

    public PlaytimeManager(LobbyCore plugin) {
        this.plugin = plugin;
        this.playtimeKey = new NamespacedKey(plugin, "total_playtime_ticks");
    }

    public void tick() {
        serverTickCounter++;
    }

    public void onJoin(Player player) {
        UUID id = player.getUniqueId();
        Long stored = player.getPersistentDataContainer().get(playtimeKey, PersistentDataType.LONG);
        cachedTotalTicks.put(id, stored != null ? stored : 0L);
        sessionStartTick.put(id, serverTickCounter);
        knownNames.put(id, player.getName());
    }

    public void onQuit(Player player) {
        flush(player);
        sessionStartTick.remove(player.getUniqueId());
    }

    /**
     * Adds the player's elapsed session time to their cached total and
     * writes it to PDC. Call periodically (e.g. every few minutes) and on quit.
     */
    public void flush(Player player) {
        UUID id = player.getUniqueId();
        Long start = sessionStartTick.get(id);
        if (start == null) return;

        long elapsed = serverTickCounter - start;
        long newTotal = cachedTotalTicks.getOrDefault(id, 0L) + elapsed;

        cachedTotalTicks.put(id, newTotal);
        sessionStartTick.put(id, serverTickCounter); // reset session start so we don't double count

        player.getPersistentDataContainer().set(playtimeKey, PersistentDataType.LONG, newTotal);
    }

    public void flushAll() {
        for (var player : plugin.getServer().getOnlinePlayers()) {
            flush(player);
        }
    }

    /**
     * Returns current total playtime in ticks for a player, including
     * their in-progress session if online.
     */
    public long getTotalTicks(Player player) {
        UUID id = player.getUniqueId();
        long cached = cachedTotalTicks.getOrDefault(id, 0L);
        Long start = sessionStartTick.get(id);
        if (start != null) {
            cached += (serverTickCounter - start);
        }
        return cached;
    }

    /**
     * Returns the top N players by total playtime, reading from each known
     * offline player's PersistentDataContainer. Only considers players who
     * have joined before (i.e. have a playerdata file).
     *
     * Note: scanning ALL offline players on a large server can be expensive;
     * this implementation caches names of players seen this session plus
     * reads Bukkit's getOfflinePlayers() which is normally a lightweight
     * array (no disk IO) of previously-seen players.
     */
    public List<PlaytimeEntry> getTopPlayers(int limit) {
        Map<UUID, Long> combined = new HashMap<>(cachedTotalTicks);

        // Add in-progress session time for currently online players
        for (var player : plugin.getServer().getOnlinePlayers()) {
            combined.put(player.getUniqueId(), getTotalTicks(player));
        }

        // Also check offline players not already in our cache (e.g. after a
        // fresh restart before they've rejoined this session)
        for (OfflinePlayer op : plugin.getServer().getOfflinePlayers()) {
            UUID id = op.getUniqueId();
            if (combined.containsKey(id)) continue;
            Long stored = op.getPersistentDataContainer().get(playtimeKey, PersistentDataType.LONG);
            if (stored != null && stored > 0) {
                combined.put(id, stored);
            }
        }

        List<PlaytimeEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : combined.entrySet()) {
            String name = knownNames.get(e.getKey());
            if (name == null) {
                OfflinePlayer op = plugin.getServer().getOfflinePlayer(e.getKey());
                name = op.getName() != null ? op.getName() : "Unknown";
            }
            entries.add(new PlaytimeEntry(e.getKey(), name, e.getValue()));
        }

        entries.sort(Comparator.comparingLong((PlaytimeEntry en) -> en.totalTicks).reversed());

        return entries.subList(0, Math.min(limit, entries.size()));
    }

    public static String formatTicksAsHoursMinutes(long ticks) {
        long totalMinutes = ticks / 1200; // 20 ticks/sec * 60 sec = 1200 ticks/min
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours + "h " + minutes + "m";
    }

    public record PlaytimeEntry(UUID uuid, String name, long totalTicks) {}
}
