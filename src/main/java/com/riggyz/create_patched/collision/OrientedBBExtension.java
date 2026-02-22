package com.riggyz.create_patched.collision;

import com.simibubi.create.foundation.collision.ContinuousOBBCollider.ContinuousSeparationManifold;

import net.minecraft.world.phys.Vec3;

/**
 * Duck interface applied to {@link com.simibubi.create.foundation.collision.OrientedBB}
 * via mixin to add a CollisionList-based intersect method.
 */
public interface OrientedBBExtension {
	ContinuousSeparationManifold create_patched$intersect(CollisionList collisionList, int bbIdx, Vec3 motion);
}
