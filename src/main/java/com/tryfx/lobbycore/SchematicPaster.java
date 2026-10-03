package com.tryfx.lobbycore;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;

/**
 * Loads a Sponge Schematic (.schem) file and pastes it into a world via the
 * WorldEdit API. Used to paste the bundled lobby structure (freelobby0.schem)
 * into the lobby world on first setup.
 */
public final class SchematicPaster {

    /**
     * Pastes the schematic at the given file so that its minimum corner
     * (origin) lands at pasteOrigin. Returns the Clipboard that was pasted,
     * so callers can compute offsets (e.g. spawn point) relative to its
     * dimensions.
     */
    public static Clipboard paste(File schematicFile, Location pasteOrigin) throws Exception {
        if (!schematicFile.exists()) {
            throw new IllegalArgumentException("Schematic file not found: " + schematicFile.getAbsolutePath());
        }

        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            throw new IllegalArgumentException("Unrecognized schematic format: " + schematicFile.getName());
        }

        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            clipboard = reader.read();
        }

        BlockVector3 target = BlockVector3.at(pasteOrigin.getBlockX(), pasteOrigin.getBlockY(), pasteOrigin.getBlockZ());

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(pasteOrigin.getWorld()))) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(target)
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(operation);
        } catch (WorldEditException e) {
            throw new RuntimeException("Failed to paste schematic", e);
        }

        return clipboard;
    }
}
