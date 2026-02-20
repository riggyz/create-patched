package com.riggyz.createpatched;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Create Patched — backports two bugfixes from Create 6.0.9+ to Forge 1.20.1.
 *
 * <ul>
 *   <li>Schematicannon NBT fix (upstream commit b91757a): prevents item duplication with
 *       NBT-bearing blocks such as Placards by including their NBT in the ItemRequirement.</li>
 *   <li>Train/contraption collision optimisation: replaces the expensive Shapes.or() shape-union
 *       with a simple AABB list to eliminate lag spikes when trains enter stations.</li>
 * </ul>
 */
@Mod(CreatePatched.MOD_ID)
public class CreatePatched {

    public static final String MOD_ID = "createpatched";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public CreatePatched() {
        LOGGER.info("Create Patched loaded — NBT and collision fixes active.");
    }
}
