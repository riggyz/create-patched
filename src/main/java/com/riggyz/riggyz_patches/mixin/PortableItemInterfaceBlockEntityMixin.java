package com.riggyz.riggyz_patches.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.actors.psi.PortableItemInterfaceBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes Portable Storage Interface intermittently failing to transfer items.
 * After capability swap + invalidation, neighbors (funnels, hoppers) are not
 * notified to re-query their cached capability references. Adding a block
 * update forces neighbors to refresh.
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/issues/7882">Create#7882</a>
 * @see <a href="https://github.com/Creators-of-Create/Create/pull/9624">Create PR #9624</a>
 */
@Mixin(value = PortableItemInterfaceBlockEntity.class, remap = false)
public abstract class PortableItemInterfaceBlockEntityMixin extends BlockEntity {

    private PortableItemInterfaceBlockEntityMixin() {
        super(null, BlockPos.ZERO, null);
    }

    @Inject(
        method = "startTransferringTo", 
        at = @At("TAIL")
    )
    private void notifyNeighborsOnStartTransfer(Contraption contraption, float distance, CallbackInfo ci) {
        Level level = this.getLevel();

        if (level != null && !level.isClientSide) {
            level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        }
    }

    @Inject(
        method = "stopTransferring", 
        at = @At("TAIL")
    )
    private void notifyNeighborsOnStopTransfer(CallbackInfo ci) {
        Level level = this.getLevel();

        if (level != null && !level.isClientSide) {
            level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        }
    }
}
