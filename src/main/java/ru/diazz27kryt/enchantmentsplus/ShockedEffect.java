package ru.diazz27kryt.enchantmentsplus;

import com.mojang.serialization.MapCodec;
import net.minecraft.enchantment.EnchantmentEffectContext;
import net.minecraft.enchantment.effect.EnchantmentEntityEffect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * A cosmetic lightning strike plus real lightning damage.
 * The cosmetic bolt is important: it creates the visual flash without
 * setting the target, nearby blocks, or item drops on fire.
 */
public record ShockedEffect() implements EnchantmentEntityEffect {
    public static final MapCodec<ShockedEffect> CODEC = MapCodec.unit(new ShockedEffect());

    @Override
    public void apply(ServerWorld world, int level, EnchantmentEffectContext context, Entity target, Vec3d pos) {
        if (!(context.owner() instanceof PlayerEntity attacker) || !(target instanceof LivingEntity victim)) {
            return;
        }

        // Only trigger during a real downward fall, not while standing or jumping.
        if (attacker.isOnGround() || attacker.getVelocity().y >= -0.01) {
            return;
        }

        LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
        bolt.refreshPositionAfterTeleport(victim.getX(), victim.getY(), victim.getZ());
        bolt.setCosmetic(true);
        world.spawnEntity(bolt);

        // Cosmetic lightning does not deal damage itself, so apply the damage once.
        victim.damage(world, world.getDamageSources().lightningBolt(), 5.0F);
    }

    @Override
    public MapCodec<? extends EnchantmentEntityEffect> getCodec() {
        return CODEC;
    }
}
