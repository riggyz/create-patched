package com.riggyz.riggyz_patches.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.mojang.blaze3d.platform.Window;
import com.simibubi.create.compat.trainmap.XaeroTrainMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The original code divides by GUI scale but omits the interface scale factor,
 * causing the overlay to be offset from the actual map.
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/issues/9414">Create#9414</a>
 */
@Mixin(value = XaeroTrainMap.class, remap = false)
public class XaeroTrainMapMixin {

    /*
     * Inject right after 'double scale = mapScale / guiScale;' is computed,
     * before it's used in pose.scale(). We redirect the pose.scale() call
     * to use the corrected scale value.
     * 
     * Original: double scale = mapScale / guiScale;
     * Fixed: double scale = mapScale / guiScale / interfaceScale;
     * where interfaceScale = (double) window.getWidth() / window.getScreenWidth()
     */
    @Inject(
        method = "onRender", 
        at = @At(
            value = "INVOKE", 
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"
        ), 
        cancellable = false
    )
    private static void fixScaleForInterfaceScaling(GuiGraphics graphics, Object screen, int mX, int mY, float pt, CallbackInfo ci, @Local(ordinal = 4) LocalDoubleRef scaleRef) {
        Window window = Minecraft.getInstance().getWindow();
        double interfaceScale = (double) window.getWidth() / window.getScreenWidth();

        if (interfaceScale != 1.0) {
            scaleRef.set(scaleRef.get() / interfaceScale);
        }
    }
}
