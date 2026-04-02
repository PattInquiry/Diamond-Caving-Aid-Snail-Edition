package com.pattinquiry.mixin;

import com.pattinquiry.Diamondcavingaidsnailedition;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RaycastContext.FluidHandling.class)
public class LavaRaycastingMixin {
    @Inject(method = "handled", at = @At("HEAD"), cancellable = true)
    private void modifyFluidHandling(FluidState state, CallbackInfoReturnable<Boolean> cir) {
        if (!Diamondcavingaidsnailedition.RAYCAST_SWITCH.get()) return;
        if (state.isIn(FluidTags.WATER)) {
            cir.setReturnValue(false);
            return;
        }
        if (state.isIn(FluidTags.LAVA)) cir.setReturnValue(true);
    }
}