package com.riggyz.create_patched.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.schematics.SchematicPrinter;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mixin that fixes an NBT dupe for schematicannon items.
 * 
 * @see https://github.com/Creators-of-Create/Create/issues/9511
 * @see https://github.com/Creators-of-Create/Create/commit/b91757a3a55205e9888e70fb5cb5c2380b4724c7
 */
@Mixin(value = SchematicPrinter.class, remap = false)
public class SchematicPrinterMixin {

    @Shadow
    private SchematicLevel blockReader;

    @ModifyArg(method = "getCurrentRequirement", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/utility/BlockHelper;prepareBlockEntityData(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)Lnet/minecraft/nbt/CompoundTag;"), index = 1)
    private BlockEntity fixBlockEntityArgument(BlockEntity original, @Local BlockPos target) {
        return blockReader.getBlockEntity(target);
    }
}
