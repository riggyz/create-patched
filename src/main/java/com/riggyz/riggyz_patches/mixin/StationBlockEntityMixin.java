package com.riggyz.riggyz_patches.mixin;

import java.util.UUID;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.compat.computercraft.AbstractComputerBehaviour;
import com.simibubi.create.compat.computercraft.events.ComputerEvent;
import com.simibubi.create.compat.computercraft.events.StationTrainPresenceEvent;
import com.simibubi.create.content.trains.station.StationBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fixes a server crash when disassembling a train via CC:Tweaked's
 * {@code station.disassemble()} Lua call. After disassembly, {@code imminentTrain}
 * is null but the DEPARTURE event path accesses it unconditionally, which leads
 * to a null Train being passed into StationPeripheral where {@code train.name}
 * causes an NPE.
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/issues/9720">Create#9720</a>
 * @see <a href="https://github.com/Creators-of-Create/Create/commit/ecbb4f20">Official Fix</a>
 */
@Mixin(value = StationBlockEntity.class, remap = false)
public class StationBlockEntityMixin {

    @Shadow
    UUID imminentTrain;

    /*
     * Wrap all calls to computerBehaviour.prepareComputerEvent() in tick().
     * If the event is a DEPARTURE event and the train inside it is null
     * (which happens when imminentTrain was null), skip the call entirely.
     */
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/compat/computercraft/AbstractComputerBehaviour;prepareComputerEvent(Lcom/simibubi/create/compat/computercraft/events/ComputerEvent;)V"
        )
    )
    private void guardNullTrainDepartureEvent(AbstractComputerBehaviour behaviour, ComputerEvent event, Operation<Void> original) {
        if (event instanceof StationTrainPresenceEvent presenceEvent && presenceEvent.train == null) {
            return;
        }

        original.call(behaviour, event);
    }
}
