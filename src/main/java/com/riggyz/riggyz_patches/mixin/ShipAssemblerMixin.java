package com.riggyz.riggyz_patches.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fixes item duplication when assembling ships with active block entity
 * inventories (e.g. Create mixers, copycats, modded storage).
 *
 * @see <a href="https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/418">VS2#418</a>
 * @see <a href="https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1688">VS2 PR #1688</a>
 */
@Mixin(
    targets = "org.valkyrienskies.mod.common.assembly.ShipAssembler",
    remap = false
)
public class ShipAssemblerMixin {

    @WrapOperation(
        method = "moveBlocksFromTo",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/Clearable;tryClear(Ljava/lang/Object;)V",
            remap = true
        )
    )
    private static void clearBlockEntityThoroughly(Object blockEntity, Operation<Void> original) {
        if (blockEntity == null) {
            return;
        }

        // For Clearable BEs, use the original tryClear. For others (e.g. Soph Storage), wipe all NBT state by loading an empty tag.
        if (blockEntity instanceof Clearable) {
            original.call(blockEntity);
        } else if (blockEntity instanceof BlockEntity be) {
            be.load(new CompoundTag());
        }

        /*
         * Remove the BE from the level before the block state change to BARRIER.
         * This prevents onRemove() from finding a BE to drop items from.
         */
        if (blockEntity instanceof BlockEntity be) {
            if (be.getLevel() instanceof ServerLevel serverLevel) {
                serverLevel.removeBlockEntity(be.getBlockPos());
            }
        }
    }
}
