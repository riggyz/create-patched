package com.riggyz.riggyz_patches.mixin;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.decoration.copycat.CopycatModel;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelData.Builder;
import net.minecraftforge.client.model.data.ModelProperty;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes copycat blocks not rendering emissive textures when the copied material
 * has {@code emissiveRendering} set to true (e.g. magma blocks, glowstone).
 * Adds an {@code IS_EMISSIVE} model property that is checked during quad
 * rendering to apply max emissivity lightmap values.
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/issues/9675">Create#9675</a>
 */
@Mixin(value = CopycatModel.class, remap = false)
public abstract class CopycatModelMixin {

    @Unique
    private static final ModelProperty<Boolean> riggyz$IS_EMISSIVE_PROPERTY = new ModelProperty<>();

    // Inject at the end of gatherModelData to add emissive property
    @Inject(method = "gatherModelData", at = @At("RETURN"))
    private void addEmissiveProperty(Builder builder, BlockAndTintGetter world, BlockPos pos, BlockState state, ModelData blockEntityData, CallbackInfoReturnable<Builder> cir) {
        BlockState material = CopycatModel.getMaterial(blockEntityData);

        if (material != null) {
            boolean isEmissive = material.emissiveRendering(world, pos);

            builder.with(riggyz$IS_EMISSIVE_PROPERTY, isEmissive);
        }
    }

    // Inject at the end of the 5-arg getQuads to apply emissivity to returned quads
    @Inject(
        method = "getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void applyEmissivity(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType, CallbackInfoReturnable<List<BakedQuad>> cir) {
        if (Boolean.TRUE.equals(data.get(riggyz$IS_EMISSIVE_PROPERTY))) {
            List<BakedQuad> quads = cir.getReturnValue();

            if (quads != null && !quads.isEmpty()) {
                List<BakedQuad> mutableQuads = new ArrayList<>(quads);

                // processInPlace mutates quad vertex data; copy to avoid corrupting cached quads
                QuadTransformers.settingMaxEmissivity().processInPlace(mutableQuads);
                cir.setReturnValue(mutableQuads);
            }
        }
    }
}
