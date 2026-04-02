package com.pattinquiry.mixin;

import com.pattinquiry.Diamondcavingaidsnailedition;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.model.SimpleBlockStateModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Predicate;

@Mixin(SimpleBlockStateModel.class)
public class AddDiamondMixin {
    @Inject(method = "emitQuads", at = @At("HEAD"))
    private void onEmitQuads(QuadEmitter emitter, BlockRenderView blockView, BlockPos pos, BlockState state, Random random, Predicate<@Nullable Direction> cullTest, CallbackInfo ci) {
        if (state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            var player = client.player;
            if (player == null) return;
            int viewDistance = client.options.getViewDistance().getValue();
            BlockPos playerPos = player.getBlockPos();
            if (isWithinViewDistance(playerPos, pos, viewDistance)) Diamondcavingaidsnailedition.addDiamondPosition(pos);
        }
    }

    private boolean isWithinViewDistance(BlockPos playerPos, BlockPos targetPos, int viewDistance) {
        double distance = playerPos.getSquaredDistance(targetPos);
        return distance <= (viewDistance * 16) * (viewDistance * 16);
    }
}