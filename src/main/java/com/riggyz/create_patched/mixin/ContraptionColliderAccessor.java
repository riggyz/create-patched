package com.riggyz.create_patched.mixin;

import java.lang.ref.WeakReference;
import java.util.Map;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionCollider;

import net.minecraft.world.entity.player.Player;

import org.apache.commons.lang3.tuple.MutablePair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor interface exposing private static fields of ContraptionCollider
 * needed by PatchedContraptionCollider.
 */
@Mixin(value = ContraptionCollider.class, remap = false)
public interface ContraptionColliderAccessor {

	@Accessor("safetyLock")
	static MutablePair<WeakReference<AbstractContraptionEntity>, Double> create_patched$getSafetyLock() {
		throw new AssertionError();
	}

	@Accessor("remoteSafetyLocks")
	static Map<AbstractContraptionEntity, Map<Player, Double>> create_patched$getRemoteSafetyLocks() {
		throw new AssertionError();
	}
}
