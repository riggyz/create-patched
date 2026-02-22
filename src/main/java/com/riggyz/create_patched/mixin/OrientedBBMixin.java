package com.riggyz.create_patched.mixin;

import com.riggyz.create_patched.collision.CollisionList;
import com.riggyz.create_patched.collision.OrientedBBExtension;
import com.simibubi.create.foundation.collision.ContinuousOBBCollider;
import com.simibubi.create.foundation.collision.ContinuousOBBCollider.ContinuousSeparationManifold;
import com.simibubi.create.foundation.collision.Matrix3d;
import com.simibubi.create.foundation.collision.OrientedBB;

import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds a CollisionList-based intersect method to OrientedBB for SoA collision checks.
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/commit/8f30c2cccce4724ddae1067e4789f56dc3ee5eda">
 *     Orient data for oriented collision</a>
 */
@Mixin(value = OrientedBB.class, remap = false)
public class OrientedBBMixin implements OrientedBBExtension {

	@Shadow
	Vec3 center;

	@Shadow
	Vec3 extents;

	@Shadow
	Matrix3d rotation;

	@Override
	@Unique
	public ContinuousSeparationManifold create_patched$intersect(CollisionList collisionList, int bbIdx, Vec3 motion) {
		Vec3 centerA = new Vec3(
			collisionList.centerX[bbIdx],
			collisionList.centerY[bbIdx],
			collisionList.centerZ[bbIdx]
		);
		Vec3 extentsA = new Vec3(
			collisionList.extentsX[bbIdx],
			collisionList.extentsY[bbIdx],
			collisionList.extentsZ[bbIdx]
		);
		return ContinuousOBBCollider.separateBBs(centerA, center, extentsA, extents, rotation, motion);
	}
}
