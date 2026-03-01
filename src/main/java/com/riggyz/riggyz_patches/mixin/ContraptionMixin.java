package com.riggyz.riggyz_patches.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Fixes the physics shape recalculation lag by skipping the expensive
 * Shapes.joinUnoptimized() + .optimize() + .toAabbs() pipeline.
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/issues/6902">Create#6902</a>
 */
@Mixin(value = Contraption.class, remap = false)
public abstract class ContraptionMixin {

	@Shadow
	protected Map<BlockPos, StructureBlockInfo> blocks;

	@Shadow
	protected ContraptionWorld collisionLevel;

	@Shadow
	private CompletableFuture<Void> simplifiedEntityColliderProvider;

	@Shadow
	public Optional<List<AABB>> simplifiedEntityColliders;

	@Shadow
	public abstract ContraptionWorld getContraptionWorld();

	/**
	 * Fix VoxelShape merging that causes lag spikes on large contraptions.
	 * Decompose each block's collision shape directly into AABBs instead.
	 *
	 * @author Riggyz
	 * @reason To replace Shapes.joinUnoptimized() with direct per-block AABB decomposition
	 */
	@Overwrite
	private void gatherBBsOffThread() {
		getContraptionWorld();

		if (simplifiedEntityColliderProvider != null) {
			simplifiedEntityColliderProvider.cancel(false);
		}

		simplifiedEntityColliderProvider = CompletableFuture.supplyAsync(() -> {
			List<AABB> result = new ArrayList<>();

			for (Map.Entry<BlockPos, StructureBlockInfo> entry : blocks.entrySet()) {
				StructureBlockInfo info = entry.getValue();
				BlockPos localPos = entry.getKey();
				VoxelShape collisionShape = info.state().getCollisionShape(collisionLevel, localPos, CollisionContext.empty());

				if (collisionShape.isEmpty()) {
					continue;
				}

				VoxelShape moved = collisionShape.move(localPos.getX(), localPos.getY(), localPos.getZ());
				result.addAll(moved.toAabbs());
			}

			return result;
		}).thenAccept(r -> {
			simplifiedEntityColliders = Optional.of(r);
		});
	}
}