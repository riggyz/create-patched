package com.riggyz.create_patched.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Mixin that fixes contraption collision lag by replacing the expensive VoxelShape
 * tree construction with direct AABB collection.
 *
 * <p>In 6.0.8, {@code invalidateColliders()} called {@code gatherBBsOffThread()} which
 * used {@code Shapes.joinUnoptimized} in a loop to build a binary tree of N VoxelShapes,
 * then called {@code optimize()} on the result. For large contraptions this created an
 * enormous object graph causing GC pressure and lag spikes.
 *
 * <p>The fix directly collects AABBs from each block's collision shape via
 * {@code VoxelShape.toAabbs()}, avoiding the VoxelShape tree entirely. This matches
 * the approach taken in Create 6.0.9 (1.21.1).
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/issues/6902">Issue #6902</a>
 */
@Mixin(value = Contraption.class, remap = false)
public abstract class ContraptionMixin {

    @Shadow
    protected Map<BlockPos, StructureBlockInfo> blocks;

    @Shadow
    protected ContraptionWorld collisionLevel;

    @Shadow
    public Optional<List<AABB>> simplifiedEntityColliders;

    @Shadow
    private CompletableFuture<Void> simplifiedEntityColliderProvider;

    @Shadow
    public abstract ContraptionWorld getContraptionWorld();

    /**
     * Replaces the original implementation that used {@code Shapes.joinUnoptimized} to
     * merge all block collision shapes into a single large VoxelShape tree and then called
     * {@code optimize()} on it. For large contraptions this created enormous intermediate
     * object trees causing severe GC pressure and lag spikes.
     *
     * <p>The new implementation directly collects AABBs from each block's collision shape
     * via {@code VoxelShape.toAabbs()}, avoiding the VoxelShape tree entirely.
     */
    @Inject(method = "invalidateColliders", at = @At("HEAD"), cancellable = true, remap = false)
    public void invalidateColliders(CallbackInfo ci) {
        if (simplifiedEntityColliderProvider != null) {
            simplifiedEntityColliderProvider.cancel(false);
            simplifiedEntityColliderProvider = null;
        }

        // Lazily initialize collisionLevel if not already set (mirrors original gatherBBsOffThread behavior).
        getContraptionWorld();

        List<AABB> bbs = new ArrayList<>(blocks.size());
        for (Map.Entry<BlockPos, StructureBlockInfo> entry : blocks.entrySet()) {
            StructureBlockInfo info = entry.getValue();
            BlockPos localPos = entry.getKey();
            VoxelShape collisionShape = info.state().getCollisionShape(collisionLevel, localPos, CollisionContext.empty());
            if (!collisionShape.isEmpty()) {
                collisionShape.move(localPos.getX(), localPos.getY(), localPos.getZ())
                    .toAabbs()
                    .forEach(bbs::add);
            }
        }
        simplifiedEntityColliders = Optional.of(bbs);
        ci.cancel();
    }
}
