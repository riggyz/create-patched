package com.riggyz.create_patched.mixin;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.riggyz.create_patched.collision.CollisionList;
import com.riggyz.create_patched.collision.CollisionListHolder;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into Contraption to:
 * 1. Add a CollisionList field for SoA collision data
 * 2. Overwrite gatherBBsOffThread() to populate it (and remove no-op .optimize())
 * 3. Null the field on invalidateColliders()
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/commit/8f30c2cccce4724ddae1067e4789f56dc3ee5eda">
 *     Orient data for oriented collision</a>
 */
@Mixin(value = Contraption.class, remap = false)
public abstract class ContraptionMixin implements CollisionListHolder {

	@Shadow
	protected Map<BlockPos, StructureBlockInfo> blocks;

	@Shadow
	protected ContraptionWorld collisionLevel;

	@Shadow
	private CompletableFuture<Void> simplifiedEntityColliderProvider;

	@Shadow
	public Optional<java.util.List<AABB>> simplifiedEntityColliders;

	@Shadow
	public abstract ContraptionWorld getContraptionWorld();

	// --- CollisionList SoA field ---

	@Unique
	private CollisionList create_patched$collisionList = null;

	@Override
	public CollisionList create_patched$getCollisionList() {
		return create_patched$collisionList;
	}

	@Override
	public void create_patched$setCollisionList(CollisionList list) {
		create_patched$collisionList = list;
	}

	// --- Null CollisionList when colliders are invalidated ---

	@Inject(method = "invalidateColliders", at = @At("HEAD"))
	private void create_patched$nullCollisionList(CallbackInfo ci) {
		create_patched$collisionList = null;
	}

	/**
	 * Overwrite gatherBBsOffThread to:
	 * - Remove the no-op .optimize() call after joinUnoptimized
	 * - Use forAllBoxes to populate a CollisionList (SoA) instead of toAabbs (List<AABB>)
	 *
	 * @author create_patched
	 * @reason Backport SoA collision data and remove no-op optimize
	 */
	@Overwrite
	private void gatherBBsOffThread() {
		getContraptionWorld();
		if (simplifiedEntityColliderProvider != null) {
			simplifiedEntityColliderProvider.cancel(false);
		}
		simplifiedEntityColliderProvider = CompletableFuture.supplyAsync(() -> {
				VoxelShape combinedShape = Shapes.empty();
				for (Map.Entry<BlockPos, StructureBlockInfo> entry : blocks.entrySet()) {
					StructureBlockInfo info = entry.getValue();
					BlockPos localPos = entry.getKey();
					VoxelShape collisionShape = info.state()
						.getCollisionShape(collisionLevel, localPos, CollisionContext.empty());
					if (collisionShape.isEmpty())
						continue;
					combinedShape = Shapes.joinUnoptimized(combinedShape,
						collisionShape.move(localPos.getX(), localPos.getY(), localPos.getZ()), BooleanOp.OR);
				}

				// Do NOT call .optimize() - it has no effect after joinUnoptimized.
				// Populate SoA CollisionList directly from the shape boxes.
				CollisionList out = new CollisionList();
				combinedShape.forAllBoxes(new CollisionList.Populate(out));
				return out;
			})
			.thenAccept(r -> {
				create_patched$collisionList = r;
				// Also set the original field to a non-empty Optional so that
				// getSimplifiedEntityColliders().isPresent() returns true,
				// signaling that pre-computed colliders are ready.
				simplifiedEntityColliders = Optional.of(Collections.emptyList());
			});
	}
}
