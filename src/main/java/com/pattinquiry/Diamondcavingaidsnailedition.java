package com.pattinquiry;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.block.Blocks;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.util.math.Direction;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.registry.tag.FluidTags;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Diamondcavingaidsnailedition implements ClientModInitializer {
    public static final String MOD_ID = "diamond-caving-aid-snail-edition";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Set<BlockPos> diamondPositions = ConcurrentHashMap.newKeySet();
    private static final int MAX_DIAMOND_LINES = 1000;
    private static class DiamondLineType {
        Vec3d start;
        Vec3d end;
        BlockPos diamondPos;
        public DiamondLineType(Vec3d start, Vec3d end, BlockPos diamondPos) {
            this.start = start;
            this.end = end;
            this.diamondPos = diamondPos;
        }
    }
    private final Deque<DiamondLineType> diamondLines = new ArrayDeque<>();
    private static final int MAX_PATH_LINES = 5000;
    private static final double MINIMUM_PATH_DISTANCE = 1.0;
    private static final double MAXIMUM_PATH_TELEPORT_DISTANCE = 1000.0;
    private Vec3d lastPathPoint = null;
    private static class PathLineType {
        Vec3d start;
        Vec3d end;
        public PathLineType(Vec3d start, Vec3d end) {
            this.start = start;
            this.end = end;
        }
    }
    private final Deque<PathLineType> pathLines = new ArrayDeque<>();
    private static KeyBinding toggleLinesKey;
    private boolean renderLines = true;
    private static final KeyBinding.Category KEY_BIND_CATEGORY = KeyBinding.Category.create(Identifier.of("diamondcavingaidsnailedition", "keys"));
    public static final ThreadLocal<Boolean> RAYCAST_SWITCH = ThreadLocal.withInitial(() -> false);
    @Override
    public void onInitializeClient() {
        LOGGER.info("Mod started.");
        toggleLinesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.diamondcavingaidsnailedition.toggle_lines", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, KEY_BIND_CATEGORY));
        // ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick(client));
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
            renderModLines(context.matrices(), camera);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            ChunkPos pos = chunk.getPos();
            diamondPositions.removeIf(blockPos -> blockPos.getX() >> 4 == pos.x && blockPos.getZ() >> 4 == pos.z);
        });
    }
    private void onClientTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;
        checkDiamondVisibility(player);
        updatePlayerPath(player);
        while (toggleLinesKey.wasPressed()) {
            renderLines = !renderLines;
            client.player.sendMessage(Text.literal("DCA SE Line Rendering: " + (renderLines ? "ON" : "OFF")), true);
        }
    }

    private void checkDiamondVisibility(ClientPlayerEntity player) {
        diamondPositions.removeIf(pos -> pos.getSquaredDistance(player.getBlockPos()) > 256 * 256);
        diamondLines.removeIf(line -> {
            BlockState state = player.getEntityWorld().getBlockState(line.diamondPos);
            return !(state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE));
        });
        Vec3d cameraPos = player.getCameraPosVec(1.0f);
        for (BlockPos pos : diamondPositions) {
            if (cameraPos.squaredDistanceTo(Vec3d.ofCenter(pos)) > 64 * 64) continue;
            BlockState blockState = player.getEntityWorld().getBlockState(pos);
            if (!(blockState.isOf(Blocks.DIAMOND_ORE) || blockState.isOf(Blocks.DEEPSLATE_DIAMOND_ORE))) continue;
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.offset(dir);
                BlockState neighborState = player.getEntityWorld().getBlockState(neighborPos);
                boolean neighborValid = neighborState.isAir() || neighborState.isOf(Blocks.CAVE_AIR) || neighborState.getFluidState().isIn(FluidTags.WATER);
                if (neighborValid) {
                    Vec3d faceCenter = new Vec3d(pos.getX() + 0.5 + 0.499 * dir.getOffsetX(), pos.getY() + 0.5 + 0.499 * dir.getOffsetY(), pos.getZ() + 0.5 + 0.499 * dir.getOffsetZ());
                    Diamondcavingaidsnailedition.RAYCAST_SWITCH.set(true);
                    HitResult result = player.getEntityWorld().raycast(new RaycastContext(cameraPos, faceCenter, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
                    Diamondcavingaidsnailedition.RAYCAST_SWITCH.set(false);
                    if (result instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)) {
                        diamondLines.addLast(new DiamondLineType(cameraPos, faceCenter, pos));
                        while (diamondLines.size() > MAX_DIAMOND_LINES) diamondLines.removeFirst();
                        break;
                    }
                }
            }
        }
    }

    private void renderModLines(MatrixStack matrices, Camera camera) {
        if (!renderLines) return;
        Vec3d camPos = camera.getPos();
        VertexConsumer consumer = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayer.getLines());
        for (DiamondLineType line : diamondLines) {
            float x1 = (float)(line.start.x - camPos.x);
            float y1 = (float)(line.start.y - camPos.y);
            float z1 = (float)(line.start.z - camPos.z);
            float x2 = (float)(line.end.x - camPos.x);
            float y2 = (float)(line.end.y - camPos.y);
            float z2 = (float)(line.end.z - camPos.z);
            // Player position end of line (orange).
            consumer.vertex(matrices.peek().getPositionMatrix(), x1, y1, z1).normal(0f, 1f, 0f).color(255, 128, 0, 255);
            // Ore position end of line (cyan).
            consumer.vertex(matrices.peek().getPositionMatrix(), x2, y2, z2).normal(0f, 1f, 0f).color(0, 255, 255, 255);
        }
        // Path lines (red).
        for (PathLineType line : pathLines) {
            float x1 = (float)(line.start.x - camPos.x);
            float y1 = (float)(line.start.y - camPos.y);
            float z1 = (float)(line.start.z - camPos.z);
            float x2 = (float)(line.end.x - camPos.x);
            float y2 = (float)(line.end.y - camPos.y);
            float z2 = (float)(line.end.z - camPos.z);
            // Previous position (red).
            consumer.vertex(matrices.peek().getPositionMatrix(), x1, y1, z1).normal(0f, 1f, 0f).color(255, 0, 0, 255);
            // New position (white).
            consumer.vertex(matrices.peek().getPositionMatrix(), x2, y2, z2).normal(0f, 1f, 0f).color(255, 255, 255, 255);
        }
    }

    private void updatePlayerPath(ClientPlayerEntity player) {
        Vec3d currentPos = new Vec3d(player.getX(), player.getY() + 0.2, player.getZ());
        if (lastPathPoint == null) {
            lastPathPoint = currentPos;
            return;
        }
        double squaredDistance = currentPos.squaredDistanceTo(lastPathPoint);
        double minDistSq = MINIMUM_PATH_DISTANCE * MINIMUM_PATH_DISTANCE;
        double teleportDistSq = MAXIMUM_PATH_TELEPORT_DISTANCE * MAXIMUM_PATH_TELEPORT_DISTANCE;
        if (squaredDistance >= teleportDistSq) {
            // Add zero-length line at new location.
            pathLines.addLast(new PathLineType(currentPos, currentPos));
            while (pathLines.size() > MAX_PATH_LINES) pathLines.removeFirst();
            lastPathPoint = currentPos;
            return;
        }
        if (squaredDistance >= minDistSq) {
            pathLines.addLast(new PathLineType(lastPathPoint, currentPos));
            while (pathLines.size() > MAX_PATH_LINES) pathLines.removeFirst();
            lastPathPoint = currentPos;
        }
    }

    public static void addDiamondPosition(BlockPos pos) {
        diamondPositions.add(pos.toImmutable());
    }
}