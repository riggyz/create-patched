package com.riggyz.riggyz_patches.mixin;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.gantry.GantryContraption;
import com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlock;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlockEntity;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;

/**
 * Fixes floating-point precision errors in gantry movement calculations.
 * In {@code checkPinionShaft()}, the expression {@code Mth.floor(currentCoord) + .5f}
 * promotes the int result to float, losing precision for large world coordinates
 * (>8M blocks). This causes the gantry to occasionally stall or skip blocks.
 * The fix changes all float literals to double in arithmetic involving double operands.
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/pull/9481">Create PR #9481</a>
 */
@Mixin(value = GantryContraptionEntity.class, remap = false)
public abstract class GantryContraptionEntityMixin extends AbstractContraptionEntity {

    @Shadow
    Direction movementAxis;

    @Shadow
    double clientOffsetDiff;

    @Shadow
    double axisMotion;

    @Shadow
    public double sequencedOffsetLimit;

    private GantryContraptionEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    /**
     * @author Riggyz
     * @reason Fix float-to-double precision loss in pinionShaft boundary check
     */
    @Overwrite
    protected void checkPinionShaft() {
        Vec3 movementVec;
        Direction facing = ((GantryContraption) contraption).getFacing();
        Vec3 currentPosition = getAnchorVec().add(.5, .5, .5);
        BlockPos gantryShaftPos = BlockPos.containing(currentPosition).relative(facing.getOpposite());
        BlockEntity be = level().getBlockEntity(gantryShaftPos);

        if (!(be instanceof GantryShaftBlockEntity gantryShaftBlockEntity) || !AllBlocks.GANTRY_SHAFT.has(be.getBlockState())) {
            if (!level().isClientSide) {
                setContraptionMotion(Vec3.ZERO);
                disassemble();
            }

            return;
        }

        BlockState blockState = be.getBlockState();
        Direction direction = blockState.getValue(GantryShaftBlock.FACING);
        float pinionMovementSpeed = gantryShaftBlockEntity.getPinionMovementSpeed();

        if (blockState.getValue(GantryShaftBlock.POWERED) || pinionMovementSpeed == 0) {
            setContraptionMotion(Vec3.ZERO);

            if (!level().isClientSide) {
                disassemble();
            }

            return;
        }

        if (sequencedOffsetLimit >= 0){
            pinionMovementSpeed = (float) Mth.clamp(pinionMovementSpeed, -sequencedOffsetLimit, sequencedOffsetLimit);
        }

        movementVec = Vec3.atLowerCornerOf(direction.getNormal()).scale(pinionMovementSpeed);

        Vec3 nextPosition = currentPosition.add(movementVec);
        double currentCoord = direction.getAxis().choose(currentPosition.x, currentPosition.y, currentPosition.z);
        double nextCoord = direction.getAxis().choose(nextPosition.x, nextPosition.y, nextPosition.z);

        // changed .5f to .5 to prevent int to float precision loss at large coords
        if ((Mth.floor(currentCoord) + .5 < nextCoord != (pinionMovementSpeed * direction.getAxisDirection().getStep() < 0))) {
            if (!gantryShaftBlockEntity.canAssembleOn()) {
                setContraptionMotion(Vec3.ZERO);

                if (!level().isClientSide) {
                    disassemble();
                }
                
                return;
            }
        }

        if (level().isClientSide) {
            return;
        }

        axisMotion = pinionMovementSpeed;
        setContraptionMotion(movementVec);
    }

    /**
     * @author Riggyz
     * @reason Fix float literal in double arithmetic for consistency with upstream
     */
    @Overwrite
    public void updateClientMotion() {
        float modifier = movementAxis.getAxisDirection().getStep();
        Vec3 motion = Vec3.atLowerCornerOf(movementAxis.getNormal()).scale((axisMotion + clientOffsetDiff * modifier / 2d) * ServerSpeedProvider.get());

        if (sequencedOffsetLimit >= 0) {
            motion = VecHelper.clampComponentWise(motion, (float) sequencedOffsetLimit);
        }

        setContraptionMotion(motion);
    }
}
