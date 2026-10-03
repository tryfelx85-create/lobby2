package com.tryfx.lobbycore;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.util.logging.Level;

/**
 * Creates the void lobby world (if it doesn't already exist) and pastes the
 * bundled freelobby0.schem structure into it at a fixed paste origin.
 *
 * Spawn point is computed as pasteOrigin + the configured offset
 * (8.5, 17, 8.5 from the schematic's minimum/origin corner, as given by the
 * operator) so it lands inside the structure exactly where intended.
 */
public final class LobbyWorldManager {

    private static final String WORLD_NAME = "tryfx_lobby";

    // Where the schematic's minimum corner is placed in the world.
    // Chosen arbitrarily away from 0,0,0 to leave room, since this is a void world.
    private static final int PASTE_ORIGIN_X = -68; // roughly centers the 137-wide structure on X=0
    private static final int PASTE_ORIGIN_Y = 4;
    private static final int PASTE_ORIGIN_Z = -79; // roughly centers the 158-long structure on Z=0

    // Spawn offset from the schematic's min corner, as given by the operator.
    private static final double SPAWN_OFFSET_X = 8.5;
    private static final double SPAWN_OFFSET_Y = 17;
    private static final double SPAWN_OFFSET_Z = 8.5;

    private final LobbyCore plugin;
    private World lobbyWorld;
    private Location spawnLocation;
    private Location pasteOrigin;

    public LobbyWorldManager(LobbyCore plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        World existing = Bukkit.getWorld(WORLD_NAME);
        boolean needsPaste = existing == null;

        if (existing != null) {
            lobbyWorld = existing;
        } else {
            WorldCreator creator = new WorldCreator(WORLD_NAME);
            creator.generator(new VoidGenerator());
            creator.generateStructures(false);
            lobbyWorld = Bukkit.createWorld(creator);

            if (lobbyWorld == null) {
                plugin.getLogger().severe("Failed to create lobby world!");
                return;
            }
        }

        lobbyWorld.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
        lobbyWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        lobbyWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        lobbyWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        lobbyWorld.setGameRule(GameRule.KEEP_INVENTORY, true);
        lobbyWorld.setGameRule(GameRule.FALL_DAMAGE, false);
        lobbyWorld.setTime(6000);

        this.pasteOrigin = new Location(lobbyWorld, PASTE_ORIGIN_X, PASTE_ORIGIN_Y, PASTE_ORIGIN_Z);

        if (needsPaste) {
            File schematicFile = new File(plugin.getDataFolder(), "schematics/freelobby0.schem");
            if (!schematicFile.exists()) {
                plugin.getLogger().warning("Lobby schematic not found at " + schematicFile.getAbsolutePath()
                        + " - lobby world created but structure was NOT pasted.");
            } else {
                try {
                    Clipboard clipboard = SchematicPaster.paste(schematicFile, pasteOrigin);
                    plugin.getLogger().info("Lobby schematic pasted ("
                            + clipboard.getDimensions().getX() + "x"
                            + clipboard.getDimensions().getY() + "x"
                            + clipboard.getDimensions().getZ() + ").");
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to paste lobby schematic", e);
                }
            }
        }

        spawnLocation = pasteOrigin.clone().add(SPAWN_OFFSET_X, SPAWN_OFFSET_Y, SPAWN_OFFSET_Z);
        spawnLocation.setYaw(0);
        spawnLocation.setPitch(0);

        lobbyWorld.setSpawnLocation(spawnLocation);
    }

    public World getLobbyWorld() {
        return lobbyWorld;
    }

    public Location getSpawnLocation() {
        return spawnLocation != null ? spawnLocation.clone() : null;
    }

    /**
     * Converts an (x, y, z) offset measured from the schematic's minimum/
     * origin corner into a real world Location, by adding it to the actual
     * paste origin. Used for anything placed relative to the schematic
     * (spawn point, portal villagers, the playtime banner) so all of these
     * stay correct together if the paste origin ever changes.
     */
    public Location offsetToWorldLocation(double offsetX, double offsetY, double offsetZ) {
        if (pasteOrigin == null) return null;
        return pasteOrigin.clone().add(offsetX, offsetY, offsetZ);
    }

    /**
     * Minimal chunk generator that produces nothing - a true void world,
     * since the lobby exists entirely as the pasted structure.
     */
    private static final class VoidGenerator extends ChunkGenerator {
        // No overrides needed: ChunkGenerator's defaults produce empty chunks
        // when generateNoise/generateSurface/etc. are left unimplemented in
        // modern Paper API (ChunkGenerator.ChunkData is left untouched).
    }
}
