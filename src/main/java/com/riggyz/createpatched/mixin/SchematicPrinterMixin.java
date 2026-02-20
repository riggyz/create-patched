package com.riggyz.createpatched.mixin;

import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixes item duplication / incorrect item consumption when the schematicannon places blocks
 * that carry NBT data and are tagged with {@code create:safe_nbt} (e.g. Placards).
 *
 * <p>Root cause: In Create 6.0.8 the {@link ItemRequirement} created for such blocks did not
 * include the block-entity NBT in the required {@link ItemStack}, so the cannon would match
 * <em>any</em> copy of that item regardless of its NBT, leading to duplication or wrong-item
 * consumption.
 *
 * <p>Upstream fix: commit {@code b91757a} in Creators-of-Create/Create — the requirement now
 * includes NBT when satisfying the item requirement for {@code SAFE_NBT} tagged blocks.
 *
 * <p>This mixin injects at the return of
 * {@link ItemRequirement#of(BlockState, BlockEntity)} and, when the block is tagged
 * {@code create:safe_nbt}, replaces each bare {@link ItemStack} in the requirement with a
 * copy that carries the tile-entity's NBT so the cannon correctly matches and consumes the
 * right item from the player's inventory.
 */
@Mixin(value = ItemRequirement.class, remap = false)
public class SchematicPrinterMixin {

    // TagKey for blocks whose item form must be matched with strict NBT equality.
    // This is the same tag that Create itself uses to mark NBT-bearing blocks (Placards, etc.).
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> SAFE_NBT_TAG =
        BlockTags.create(new ResourceLocation("create", "safe_nbt"));

    /**
     * After {@code ItemRequirement.of()} resolves the required stacks, check whether the block
     * is tagged {@code create:safe_nbt}.  If so, copy the block-entity NBT onto each required
     * {@link ItemStack} so the schematicannon performs strict NBT matching when consuming items.
     *
     * @param state       The {@link BlockState} being placed by the cannon.
     * @param blockEntity The tile entity at that position (may be {@code null}).
     * @param cir         Callback info carrying the computed {@link ItemRequirement}.
     */
    @Inject(
        method = "of(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)Lcom/simibubi/create/content/schematics/requirement/ItemRequirement;",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void injectNbtAwareRequirement(
            BlockState state,
            BlockEntity blockEntity,
            CallbackInfoReturnable<ItemRequirement> cir) {

        if (blockEntity == null) {
            return;
        }

        if (!state.is(SAFE_NBT_TAG)) {
            return;
        }

        ItemRequirement original = cir.getReturnValue();
        if (original == null || original.isInvalid() || original.isEmpty()) {
            return;
        }

        // Copy the block-entity NBT onto each required ItemStack so the cannon uses
        // strict NBT matching, preventing duplication of NBT-bearing items.
        CompoundTag beNbt = blockEntity.saveWithoutMetadata();
        List<ItemRequirement.StackRequirement> patched = new ArrayList<>();
        for (ItemRequirement.StackRequirement req : original.getRequiredItems()) {
            ItemStack nbtStack = req.stack.copy();
            nbtStack.setTag(beNbt);
            patched.add(new ItemRequirement.StackRequirement(nbtStack, req.usage));
        }
        cir.setReturnValue(new ItemRequirement(patched));
    }
}
