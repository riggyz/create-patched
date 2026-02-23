package com.riggyz.create_patched.mixin;

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
 * Instead, we decompose each block's collision shape directly into AABBs.
 * This is O(n) instead of O(n²) and produces the same List<AABB> result
 * that the rest of the existing collision code already consumes.
 *
 * No other classes need to be changed.
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
	 * @author create_patched
	 * @reason Fix O(n²) VoxelShape merging that causes lag spikes on large contraptions.
	 *         Decompose each block's collision shape directly into AABBs instead.
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
					VoxelShape collisionShape = info.state()
						.getCollisionShape(collisionLevel, localPos, CollisionContext.empty());
					if (collisionShape.isEmpty())
						continue;
					VoxelShape moved = collisionShape.move(
						localPos.getX(), localPos.getY(), localPos.getZ());
					result.addAll(moved.toAabbs());
				}
				return result;
			})
			.thenAccept(r -> {
				simplifiedEntityColliders = Optional.of(r);
			});
	}
}