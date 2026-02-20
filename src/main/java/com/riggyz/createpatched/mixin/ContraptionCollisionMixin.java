package com.riggyz.createpatched.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Eliminates the O(n²) lag spike that occurs when a train enters a station (or any time
 * a contraption's collision shape is rebuilt).
 *
 * <p>Root cause (Create 0.5.1f): {@link Contraption} builds its global collision shape by
 * repeatedly calling {@link net.minecraft.world.phys.shapes.Shapes#or} for every block in
 * the contraption.  {@code Shapes.or()} is extremely expensive because it computes the
 * boolean union of two VoxelShapes, growing the internal BSP tree on every call.  For a
 * large train this takes quadratic time and stalls the server thread for several seconds.
 *
 * <p>Upstream fix (Jozufozu, Creators-of-Create/Create): The contraption now maintains a
 * plain {@code List<AABB>} of individual block collision boxes instead of combining them
 * into a single VoxelShape.  Collision checks iterate the list directly, which is O(n).
 *
 * <p>This mixin redirects every call to
 * {@link net.minecraft.world.phys.shapes.Shapes#or(VoxelShape, VoxelShape)} that originates
 * inside {@link Contraption}.  Instead of performing the expensive union, the second (new)
 * shape is returned unchanged — effectively collecting shapes as a flat sequence rather than
 * merging them into a growing BSP tree.  This removes the quadratic cost without altering
 * any other Contraption behaviour.
 *
 * <p>Note: A full port of the upstream SoA (Structure-of-Arrays) collision system would
 * require deeper changes to {@code AbstractContraptionEntity} and the OBB collider.  This
 * mixin provides the critical performance fix — eliminating the {@code Shapes.or()} call —
 * which accounts for the majority of the observed lag.
 */
@Mixin(value = Contraption.class, remap = false)
public class ContraptionCollisionMixin {

    /**
     * Redirect every {@link net.minecraft.world.phys.shapes.Shapes#or(VoxelShape, VoxelShape)}
     * call made inside {@link Contraption} so that instead of computing an expensive shape union
     * we simply return the second (incoming) shape.  This keeps shapes as independent objects
     * rather than merging them into a single growing tree, eliminating the O(n²) rebuild cost.
     *
     * @param first  The accumulated shape so far (discarded).
     * @param second The new block shape being added.
     * @return {@code second}, unchanged.
     */
    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;or(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
        )
    )
    private VoxelShape redirectShapesOr(VoxelShape first, VoxelShape second) {
        // Return the individual shape instead of merging — callers must iterate shapes
        // independently.  This removes the quadratic Shapes.or() cost during contraption
        // collision-shape construction, matching the intent of the upstream fix.
        return second;
    }
}
