package com.riggyz.riggyz_patches.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.compat.trainmap.XaeroTrainMap;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The original code divides by GUI scale but omits the interface scale factor,
 * causing the overlay to be offset from the actual map.
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/issues/9414">Create#9414</a>
 */
@Mixin(value = XaeroTrainMap.class, remap = false)
public class XaeroTrainMapMixin {

    @WrapOperation(
        method = "onRender", 
        at = @At(
            value = "INVOKE", 
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",
            remap = true
        )
    )
    private static void applyInterfaceScale(PoseStack pose, float x, float y, float z, Operation<Void> original) {
        Window window = Minecraft.getInstance().getWindow();
        double interfaceScale = (double) window.getWidth() / window.getScreenWidth();

        if (interfaceScale != 1.0d) {
            x /= (float) interfaceScale;
            y /= (float) interfaceScale;
        }

        original.call(pose, x, y, z);
    }
}
