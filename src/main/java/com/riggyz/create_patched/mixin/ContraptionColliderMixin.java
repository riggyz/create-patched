package com.riggyz.create_patched.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.contraptions.PatchedContraptionCollider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin mixin that redirects ContraptionCollider.collideEntities() to our patched version
 * which uses SoA CollisionList for cache-friendly collision checks.
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/commit/8f30c2cccce4724ddae1067e4789f56dc3ee5eda">
 *     Orient data for oriented collision</a>
 */
@Mixin(value = ContraptionCollider.class, remap = false)
public class ContraptionColliderMixin {

	@Inject(method = "collideEntities", at = @At("HEAD"), cancellable = true)
	private static void create_patched$replaceCollideEntities(AbstractContraptionEntity contraptionEntity, CallbackInfo ci) {
		PatchedContraptionCollider.collideEntities(contraptionEntity);
		ci.cancel();
	}
}
