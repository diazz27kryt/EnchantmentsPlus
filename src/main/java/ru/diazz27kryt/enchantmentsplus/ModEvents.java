package ru.diazz27kryt.enchantmentsplus;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

public final class ModEvents {
    private static final String SHARD_TARGET = "EnchantmentsPlusTarget";
    private static final String SHARD_DIMENSION = "Dimension";
    private static final String SHARD_X = "X";
    private static final String SHARD_Y = "Y";
    private static final String SHARD_Z = "Z";

    private ModEvents() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(ModEvents::onBlockBroken);
        UseItemCallback.EVENT.register(ModEvents::onUseItem);
        ServerTickEvents.END_SERVER_TICK.register(ModEvents::onServerTick);
    }

    private static void onBlockBroken(World world, PlayerEntity player, BlockPos origin,
                                      BlockState originState, net.minecraft.block.entity.BlockEntity blockEntity) {
        if (!(world instanceof ServerWorld serverWorld) || player.isCreative() || player.isSpectator()) {
            return;
        }

        ItemStack tool = player.getMainHandStack();
        if (tool.isEmpty() || !tool.isIn(ItemTags.PICKAXES)) {
            return;
        }

        int level = getEnchantmentLevel(serverWorld, tool, "miner");
        if (level <= 0) {
            return;
        }

        int radius = level == 1 ? 1 : 2;
        Vec3d look = player.getRotationVec(1.0F);
        // Mine a 3x3 or 5x5 plane facing the direction the player is looking.
        boolean horizontalPlane = Math.abs(look.y) > 0.72;
        boolean sidePlane = !horizontalPlane && Math.abs(look.x) > Math.abs(look.z);

        int broken = 0;
        for (int a = -radius; a <= radius && broken < 24; a++) {
            for (int b = -radius; b <= radius && broken < 24; b++) {
                if (a == 0 && b == 0) continue;
                if (tool.isEmpty() || player.getMainHandStack().isEmpty()) return;

                BlockPos target;
                if (horizontalPlane) {
                    target = origin.add(a, 0, b);
                } else if (sidePlane) {
                    target = origin.add(0, a, b);
                } else {
                    target = origin.add(a, b, 0);
                }

                if (!serverWorld.isInBuildLimit(target) || !serverWorld.isChunkLoaded(target)
                        || !serverWorld.canEntityModifyAt(player, target)) {
                    continue;
                }

                BlockState state = serverWorld.getBlockState(target);
                if (state.isAir() || state.hasBlockEntity() || serverWorld.getBlockEntity(target) != null
                        || state.getHardness(serverWorld, target) < 0.0F || !tool.isSuitableFor(state)) {
                    continue;
                }

                // Remove without vanilla's empty-tool drop path, then run the normal
                // player after-break logic with the actual pickaxe (Silk Touch/Fortune).
                if (serverWorld.breakBlock(target, false, player)) {
                    state.getBlock().onBroken(serverWorld, target, state);
                    state.getBlock().afterBreak(serverWorld, player, target, state, null, tool);
                    state.onStacksDropped(serverWorld, target, tool, true);
                    tool.damage(1, player, EquipmentSlot.MAINHAND);
                    broken++;
                }
            }
        }
    }

    private static ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return ActionResult.PASS;

        NbtCompound data = component.copyNbt();
        if (data.getString(SHARD_TARGET).orElse("").isEmpty()) return ActionResult.PASS;

        if (player instanceof ServerPlayerEntity serverPlayer) {
            String dimension = data.getString(SHARD_DIMENSION).orElse("unknown");
            serverPlayer.sendMessage(Text.translatable(
                    "message.enchantmentsplus.trident_location",
                    dimension,
                    data.getInt(SHARD_X).orElse(0),
                    data.getInt(SHARD_Y).orElse(0),
                    data.getInt(SHARD_Z).orElse(0)
            ), true);
        }
        return ActionResult.SUCCESS;
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            for (TridentEntity trident : world.getEntitiesByType(TypeFilter.instanceOf(TridentEntity.class), entity -> true)) {
                if (!(trident.getOwner() instanceof ServerPlayerEntity owner)) continue;

                ItemStack thrownStack = trident.getItemStack();
                int trackerLevel = getEnchantmentLevel(world, thrownStack, "trident_tracker");
                int poseidonLevel = getEnchantmentLevel(world, thrownStack, "poseidon");

                if (trackerLevel > 0) {
                    ensureShard(owner, trident, world);
                    updateShard(owner, trident, world);
                }

                if (poseidonLevel > 0 && owner.isSneaking() && !owner.isSpectator()) {
                    pullPlayerToTrident(owner, trident);
                }
            }
        }
    }

    private static void ensureShard(ServerPlayerEntity player, TridentEntity trident, ServerWorld world) {
        UUID targetId = trident.getUuid();
        if (findShard(player, targetId) != null) return;

        ItemStack shard = new ItemStack(Items.AMETHYST_SHARD);
        shard.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item.enchantmentsplus.trident_shard"));
        shard.set(DataComponentTypes.MAX_STACK_SIZE, 1);

        NbtCompound data = new NbtCompound();
        data.putString(SHARD_TARGET, targetId.toString());
        writePosition(data, trident, world);
        shard.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));

        player.getInventory().offerOrDrop(shard);
        player.sendMessage(Text.translatable("message.enchantmentsplus.shard_given"), true);
    }

    private static void updateShard(ServerPlayerEntity player, TridentEntity trident, ServerWorld world) {
        ItemStack shard = findShard(player, trident.getUuid());
        if (shard == null) return;

        NbtComponent component = shard.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return;
        NbtCompound data = component.copyNbt();

        BlockPos pos = trident.getBlockPos();
        String dimension = world.getRegistryKey().getValue().toString();
        if (!dimension.equals(data.getString(SHARD_DIMENSION))
                || data.getInt(SHARD_X).orElse(0) != pos.getX()
                || data.getInt(SHARD_Y).orElse(0) != pos.getY()
                || data.getInt(SHARD_Z).orElse(0) != pos.getZ()) {
            writePosition(data, trident, world);
            shard.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
        }
    }

    private static void writePosition(NbtCompound data, TridentEntity trident, ServerWorld world) {
        BlockPos pos = trident.getBlockPos();
        data.putString(SHARD_DIMENSION, world.getRegistryKey().getValue().toString());
        data.putInt(SHARD_X, pos.getX());
        data.putInt(SHARD_Y, pos.getY());
        data.putInt(SHARD_Z, pos.getZ());
    }

    private static ItemStack findShard(ServerPlayerEntity player, UUID targetId) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(Items.AMETHYST_SHARD)) continue;
            NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (component != null) {
                NbtCompound data = component.copyNbt();
                if (targetId.toString().equals(data.getString(SHARD_TARGET).orElse(""))) {
                    return stack;
                }
            }
        }
        return null;
    }

    private static void pullPlayerToTrident(ServerPlayerEntity player, TridentEntity trident) {
        Vec3d difference = new Vec3d(trident.getX() - player.getX(), trident.getY() - player.getY(), trident.getZ() - player.getZ());
        double distance = difference.length();
        if (distance < 1.35) return;

        double strength = Math.min(0.30, Math.max(0.07, distance * 0.018));
        Vec3d pull = difference.normalize().multiply(strength);
        Vec3d velocity = player.getVelocity().multiply(0.82).add(pull);
        double maxSpeed = 1.15;
        if (velocity.lengthSquared() > maxSpeed * maxSpeed) {
            velocity = velocity.normalize().multiply(maxSpeed);
        }

        player.setVelocity(velocity);
    }

    private static int getEnchantmentLevel(ServerWorld world, ItemStack stack, String path) {
        Registry<Enchantment> registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        RegistryEntry<Enchantment> entry = registry.getEntry(Identifier.of(EnchantmentsPlus.MOD_ID, path))
                .orElse(null);
        return entry == null ? 0 : EnchantmentHelper.getLevel(entry, stack);
    }
}
