package com.yanbu3.horrorheads;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Compatibility addon for the All the Creatures PTSD edition pack.
 * Actual combat mobs are targetable; scripted jumpscare-only apparitions are skipped.
 * Build with ForgeGradle's reobfJar, not against local hand-made API stubs.
 */
@Mod(HorrorHeads.MOD_ID)
public final class HorrorHeads {
    public static final String MOD_ID = "yanbu3_horror_heads";
    private static final String STATS_KEY = "yanbu3_hh_v020_balanced";
    private static final String REWARD_KEY = "yanbu3_hh_head_dropped";
    private static final double HEALTH = 40.0D;
    private static final double ATTACK = 6.0D;

    private static final Set<String> SOURCES = new HashSet<>(Arrays.asList(
        "cavedweller", "theintruder", "intruder", "knocker", "nightdweller",
        "manfromthefog", "midnightlurker", "irritator", "cantbreathe",
        "boiled", "dangersinthedarkness", "shyguy", "rootoffear",
        "theboyinthebath", "scp096", "whisperwoods", "theonewhowatches",
        "sirenhead", "themimicer", "mimicer"
    ));
    private static final String[] AMBIENT_TOKENS = {
        "observer", "hallucination", "fake", "jumpscare", "ambient",
        "apparition", "watching", "shadow", "ghost", "eye", "illusion",
        "decoy", "spectator", "spirit", "event", "scareonly"
    };

    public HorrorHeads() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    private static String compact(String s) {
        return s.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private static ResourceLocation mobId(LivingEntity entity) {
        return ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
    }

    private static boolean eligible(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return false;
        ResourceLocation id = mobId(entity);
        if (id == null || "minecraft".equals(id.getNamespace())) return false;
        String namespace = compact(id.getNamespace());
        if (!SOURCES.contains(namespace)) return false;
        String path = compact(id.getPath());
        for (String token : AMBIENT_TOKENS) if (path.contains(token)) return false;
        return entity instanceof Monster || mob.getTarget() instanceof Player;
    }

    private static void balance(LivingEntity mob) {
        if (!eligible(mob)) return;
        CompoundTag data = mob.getPersistentData();
        if (data.getBoolean(STATS_KEY)) return;
        AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            double oldMax = maxHealth.getBaseValue();
            if (oldMax < HEALTH) {
                maxHealth.setBaseValue(HEALTH);
                mob.setHealth((float) HEALTH);
            }
        }
        AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null && attack.getBaseValue() < ATTACK) {
            attack.setBaseValue(ATTACK);
        }
        data.putBoolean(STATS_KEY, true);
    }

    @SubscribeEvent
    public void onMobLoaded(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity mob) balance(mob);
    }

    @SubscribeEvent
    public void onPlayerHit(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level.isClientSide) return;
        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity mob) || !eligible(mob) || !mob.isAlive()) return;
        balance(mob);
        float charge = Math.max(0.2f, player.getAttackStrengthScale(0.5f));
        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = Math.max(1.0f, base * charge);
        event.setCanceled(true);
        float left = mob.getHealth() - damage;
        if (left > 0) {
            mob.setHealth(left);
        } else {
            rewardHead(mob);
            DamageSource cause = DamageSource.playerAttack(player);
            mob.setHealth(0.0f);
            mob.die(cause);
            if (!mob.isRemoved()) mob.discard();
        }
    }

    @SubscribeEvent
    public void onDeathDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob.level.isClientSide || !eligible(mob)) return;
        if (!(event.getSource().getEntity() instanceof Player)) return;
        rewardHead(mob);
    }

    private static void rewardHead(LivingEntity mob) {
        CompoundTag persistent = mob.getPersistentData();
        if (persistent.getBoolean(REWARD_KEY)) return;
        persistent.putBoolean(REWARD_KEY, true);
        ResourceLocation id = mobId(mob);
        if (id == null) return;
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        String name = mob.getType().getDescription().getString();
        if (name.trim().isEmpty()) name = id.getPath().replace('_', ' ');
        head.setHoverName(Component.literal(name + " - Horror Head"));
        head.getOrCreateTag().putString("HorrorCreatureId", id.toString());
        head.getOrCreateTag().putString("HorrorHeadsMod", MOD_ID);
        mob.spawnAtLocation(head);
    }
}
