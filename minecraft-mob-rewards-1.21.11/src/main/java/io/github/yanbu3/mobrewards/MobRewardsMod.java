package io.github.yanbu3.mobrewards;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MobRewardsMod implements ModInitializer {
    public static final String MOD_ID = "mobrewards";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Progress progress;
    private static long ticks = 0;

    public enum Reward { ZOMBIE, SKELETON, CREEPER, ENDERMAN, SPIDER, WITCH, BLAZE, GHAST, PIGLIN, WARDEN }

    public static final Item TNT_CHARGE = register("tnt_charge", new TNTChargeItem(properties("tnt_charge").stacksTo(16)));
    public static final Item GHAST_CANNON = register("ghast_cannon", new GhastCannonItem(properties("ghast_cannon").stacksTo(1)));
    public static final Item FLIGHT_TOGGLE = register("flight_toggle", new FlightToggleItem(properties("flight_toggle").stacksTo(1)));
    public static final Item SONIC_FOCUS = register("sonic_focus", new SonicFocusItem(properties("sonic_focus").stacksTo(1)));

    private static Item.Properties properties(String name) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(name)));
    }
    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, id(name), item);
    }
    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name);
    }
    public static boolean owns(ServerPlayer player, Reward reward) {
        return progress != null && progress.owns(player.getUUID(), reward);
    }
    private static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[Mob Rewards] " + text));
    }
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
    /** Spawn rewards in the world at the defeated mob, never invisibly into inventory. */
    private static void dropAtMob(LivingEntity mob, ItemStack stack) {
        if (stack.isEmpty()) return;
        ServerLevel world = (ServerLevel) mob.level();
        ItemEntity dropped = new ItemEntity(world, mob.getX(), mob.getY() + 0.35, mob.getZ(), stack);
        world.addFreshEntity(dropped);
    }
    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    @Override
    public void onInitialize() {
        // Register a server -> client packet before a client registers its receiver.
        PayloadTypeRegistry.playS2C().register(SpiderClimbPayload.TYPE, SpiderClimbPayload.CODEC);
        // Touch custom item registrations before registry freeze.
        LOGGER.info("Mob Rewards items registered for Minecraft 1.21.11");
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            progress = Progress.load(server.getWorldPath(LevelResource.ROOT).resolve("mobrewards-progress.json"));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (progress != null) progress.save();
            progress = null;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            // Count actual kills credited to the player (melee or a projectile fired by them),
            // not kills by pets, another player or the environment.
            if (!(source.getEntity() instanceof ServerPlayer player)) return;
            Reward reward = rewardFor(entity.getType());
            if (reward == null || progress == null || !progress.isHealthy()) return;
            boolean newlyUnlocked = progress.grant(player.getUUID(), reward);
            // Keep persistent ability progress, but NEVER gate future item rewards on the flag.
            award(player, entity, reward, newlyUnlocked);
            message(player, "Reward for " + reward.name() + " " +
                    (newlyUnlocked ? "unlocked and granted!" : "granted again!"));
        });
        // Reject poison before it can be applied; tick-based cleanup is a backup.
        ServerMobEffectEvents.ALLOW_ADD.register((effect, entity, context) ->
            !(entity instanceof ServerPlayer player &&
              owns(player, Reward.WITCH) &&
              effect.getEffect().equals(MobEffects.POISON))
        );
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ++ticks;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                updatePlayer(player);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                updatePlayer(handler.player);
                syncSpiderClimb(handler.player); // Re-enable across death/relogin/dimensions
            });
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            dispatcher.register(Commands.literal("mobpowers")
                .then(Commands.literal("flight").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    toggleFlight(player);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("restore").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    restoreUtilities(player);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    for (Reward r : Reward.values()) {
                        message(player, r.name() + ": " + (owns(player, r) ? "unlocked" : "locked"));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    message(player, "Use /mobpowers status, /mobpowers flight, or /mobpowers restore");
                    return Command.SINGLE_SUCCESS;
                }));
        });
    }

    private static void syncSpiderClimb(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, SpiderClimbPayload.TYPE)) {
            ServerPlayNetworking.send(player, new SpiderClimbPayload(owns(player, Reward.SPIDER)));
        }
    }

    private static Reward rewardFor(EntityType<?> type) {
        if (type == EntityType.ZOMBIE) return Reward.ZOMBIE;
        if (type == EntityType.SKELETON) return Reward.SKELETON;
        if (type == EntityType.CREEPER) return Reward.CREEPER;
        if (type == EntityType.ENDERMAN) return Reward.ENDERMAN;
        if (type == EntityType.SPIDER) return Reward.SPIDER;
        if (type == EntityType.WITCH) return Reward.WITCH;
        if (type == EntityType.BLAZE) return Reward.BLAZE;
        if (type == EntityType.GHAST) return Reward.GHAST;
        if (type == EntityType.PIGLIN) return Reward.PIGLIN;
        if (type == EntityType.WARDEN) return Reward.WARDEN;
        return null;
    }

    private static void award(ServerPlayer player, LivingEntity killed, Reward reward, boolean newlyUnlocked) {
        switch (reward) {
            case ZOMBIE -> {
                applyEffects(player);
                message(player, "Permanent Strength II active, including after respawn.");
            }
            case SKELETON -> {
                ItemStack bow = named(Items.BOW, "Undead Slayer Bow");
                var enchantments = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                bow.enchant(enchantments.getOrThrow(Enchantments.POWER), 5);
                bow.enchant(enchantments.getOrThrow(Enchantments.INFINITY), 1);
                dropAtMob(killed, bow);
                dropAtMob(killed, new ItemStack(Items.ARROW, 64));
                message(player, "Power V + Infinity bow, plus 64 arrows. Keep at least one normal arrow.");
            }
            case CREEPER -> {
                dropAtMob(killed, new ItemStack(TNT_CHARGE, 5));
                message(player, "5 TNT charges: right-click a target within 30 blocks.");
            }
            case ENDERMAN -> {
                dropAtMob(killed, new ItemStack(Items.ENDER_PEARL, 4));
                message(player, "4 Ender Pearls dropped.");
            }
            case SPIDER -> {
                // The player's client must know about this persistent unlock to apply
                // movement prediction locally. No command or item is necessary.
                syncSpiderClimb(player);
                message(player, "Spider Climb unlocked! Hold W while facing a wall to climb; Shift to stop.");
            }
            case WITCH -> {
                applyEffects(player);
                message(player, "Permanent Regeneration I and Poison immunity active.");
            }
            case BLAZE -> {
                // Activate immediately on kill without requiring any command or click.
                progress.setFlight(player.getUUID(), true);
                if (!player.isCreative() && !player.isSpectator()) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
                dropAtMob(killed, named(FLIGHT_TOGGLE, "Flight Toggle"));
                message(player, "Flight ON automatically! Double-tap Jump to fly; use the dropped Flight Toggle to turn it off/on.");
            }
            case GHAST -> {
                dropAtMob(killed, named(GHAST_CANNON, "Ghast Cannon"));
                message(player, "Right-click Ghast Cannon to fire a Ghast fireball.");
            }
            case PIGLIN -> {
                dropAtMob(killed, new ItemStack(Items.GOLD_INGOT, 32));
                message(player, "32 gold ingots for Piglin bartering.");
            }
            case WARDEN -> {
                dropAtMob(killed, named(SONIC_FOCUS, "Sonic Focus"));
                message(player, "Right-click Sonic Focus toward one hostile mob (4 second cooldown).");
            }
        }
    }

    private static void applyEffects(ServerPlayer p) {
        if (owns(p, Reward.ZOMBIE)) {
            MobEffectInstance instance = p.getEffect(MobEffects.STRENGTH);
            if (instance == null || instance.getAmplifier() < 1 || instance.getDuration() < 100) {
                p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 240, 1, true, false, true));
            }
        }
        if (owns(p, Reward.WITCH)) {
            p.removeEffect(MobEffects.POISON);
            MobEffectInstance instance = p.getEffect(MobEffects.REGENERATION);
            if (instance == null || instance.getDuration() < 100) {
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 240, 0, true, false, true));
            }
        }
    }

    private static void updatePlayer(ServerPlayer player) {
        if (progress == null) return;
        // Poison is removed every tick, before the next living-entity effect tick.
        if (owns(player, Reward.WITCH)) player.removeEffect(MobEffects.POISON);
        if (ticks % 40 == 0) {
            applyEffects(player);
            if (owns(player, Reward.BLAZE) && progress.flightOn(player.getUUID())
                    && !player.isCreative() && !player.isSpectator()
                    && !player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
        }
        // Server-side assist; the client also applies upward movement on its own tick
        // so vanilla client movement prediction will not override the climb.
        if (owns(player, Reward.SPIDER) && player.horizontalCollision && !player.isShiftKeyDown()
                && !player.getAbilities().flying) {
            Vec3 old = player.getDeltaMovement();
            if (old.y < 0.28) {
                player.setDeltaMovement(old.x, 0.28, old.z);
                player.hurtMarked = true;
            }
            player.resetFallDistance();
        }
    }

    private static void toggleFlight(ServerPlayer player) {
        if (!owns(player, Reward.BLAZE)) {
            message(player, "First defeat a Blaze yourself to unlock flight.");
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            message(player, "Flight toggle is intended for Survival / Adventure mode.");
            return;
        }
        boolean turnOn = !progress.flightOn(player.getUUID());
        progress.setFlight(player.getUUID(), turnOn);
        player.getAbilities().mayfly = turnOn;
        if (!turnOn) player.getAbilities().flying = false;
        player.onUpdateAbilities();
        message(player, turnOn ? "Flight ON (double-tap Jump to fly)." : "Flight OFF.");
    }

    private static boolean hasItem(ServerPlayer player, Item item) {
        if (player.getMainHandItem().is(item) || player.getOffhandItem().is(item)) return true;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (player.getInventory().getItem(i).is(item)) return true;
        return false;
    }
    private static void restoreUtilities(ServerPlayer player) {
        if (owns(player, Reward.BLAZE) && !hasItem(player, FLIGHT_TOGGLE))
            give(player, named(FLIGHT_TOGGLE, "Flight Toggle"));
        if (owns(player, Reward.GHAST) && !hasItem(player, GHAST_CANNON))
            give(player, named(GHAST_CANNON, "Ghast Cannon"));
        if (owns(player, Reward.WARDEN) && !hasItem(player, SONIC_FOCUS))
            give(player, named(SONIC_FOCUS, "Sonic Focus"));
        message(player, "Missing reusable ability items restored. Consumable rewards cannot be restored.");
    }

    private static class TNTChargeItem extends Item {
        TNTChargeItem(Properties settings) { super(settings); }
        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            if (!owns(sp, Reward.CREEPER)) return InteractionResult.FAIL;
            ItemStack stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
            HitResult result = player.pick(30.0, 1.0f, false);
            Vec3 hit = result.getType() == HitResult.Type.MISS
                    ? player.getEyePosition().add(player.getLookAngle().scale(30))
                    : result.getLocation();
            ((ServerLevel) level).explode(player, hit.x, hit.y, hit.z,
                    3.0f, Level.ExplosionInteraction.TNT);
            if (!player.isCreative()) stack.shrink(1);
            player.getCooldowns().addCooldown(stack, 12);
            return InteractionResult.SUCCESS;
        }
    }

    private static class GhastCannonItem extends Item {
        GhastCannonItem(Properties settings) { super(settings); }
        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            if (!owns(sp, Reward.GHAST)) return InteractionResult.FAIL;
            ItemStack stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
            Vec3 aim = player.getLookAngle();
            LargeFireball fireball = new LargeFireball(level, player, aim, 2);
            fireball.setPos(player.getEyePosition().add(aim.scale(1.5)));
            level.addFreshEntity(fireball);
            level.playSound(null, player.blockPosition(), SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
            player.getCooldowns().addCooldown(stack, 30);
            return InteractionResult.SUCCESS;
        }
    }

    private static class FlightToggleItem extends Item {
        FlightToggleItem(Properties settings) { super(settings); }
        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer sp) toggleFlight(sp);
            return InteractionResult.SUCCESS;
        }
    }

    private static class SonicFocusItem extends Item {
        SonicFocusItem(Properties settings) { super(settings); }
        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            if (!owns(sp, Reward.WARDEN)) return InteractionResult.FAIL;
            ItemStack stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
            Vec3 eye = player.getEyePosition();
            Vec3 direction = player.getLookAngle().normalize();
            HitResult blockHit = player.pick(32.0, 1.0f, false);
            double range = blockHit.getType() == HitResult.Type.MISS
                    ? 32.0 : Math.min(32.0, eye.distanceTo(blockHit.getLocation()));
            AABB area = new AABB(eye, eye.add(direction.scale(range))).inflate(2.0);
            LivingEntity target = null;
            double nearest = range + 1.0;
            ServerLevel serverLevel = (ServerLevel) level;
            for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e instanceof Enemy && e.isAlive())) {
                Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
                double along = to.dot(direction);
                if (along <= 0 || along > range || along >= nearest) continue;
                double miss = to.subtract(direction.scale(along)).length();
                if (miss > Math.max(0.9, e.getBbWidth() / 2.0 + 0.3)) continue;
                target = e;
                nearest = along;
            }
            if (target == null) {
                message(sp, "No hostile mob in your line of sight (up to 32 blocks).");
                return InteractionResult.FAIL;
            }
            for (int n = 1; n <= 12; n++) {
                Vec3 p = eye.add(direction.scale(nearest * n / 12.0));
                serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.PLAYERS, 2f, 1f);
            target.hurtServer(serverLevel, level.damageSources().sonicBoom(player), 10000.0f);
            player.getCooldowns().addCooldown(stack, 80);
            return InteractionResult.SUCCESS;
        }
    }

    /**
     * Per-world data storage, with an atomic replace where supported. UUID rather than player name.
     * A durable grant is written BEFORE any items are delivered.
     */
    private static class Progress {
        private final Path file;
        private final Map<UUID, EnumSet<Reward>> flags = new HashMap<>();
        private final Map<UUID, Boolean> flight = new HashMap<>();

        private Progress(Path file) { this.file = file; }
        static Progress load(Path file) {
            Progress p = new Progress(file);
            if (!Files.exists(file)) return p;
            try {
                JsonObject data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
                if (data == null) return p;
                for (var entry : data.entrySet()) {
                    try {
                        UUID id = UUID.fromString(entry.getKey());
                        JsonObject record = entry.getValue().getAsJsonObject();
                        EnumSet<Reward> set = EnumSet.noneOf(Reward.class);
                        if (record.has("rewards")) for (JsonElement e : record.getAsJsonArray("rewards")) {
                            try { set.add(Reward.valueOf(e.getAsString())); }
                            catch (IllegalArgumentException ignored) { }
                        }
                        p.flags.put(id, set);
                        if (record.has("flight")) p.flight.put(id, record.get("flight").getAsBoolean());
                    } catch (RuntimeException bad) {
                        LOGGER.warn("Skipping invalid player entry in {}", file, bad);
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("Could not read progress at {}! Existing progress will not be overwritten until a new grant.", file, ex);
                p.readFailed = true;
            }
            return p;
        }
        private boolean readFailed = false;
        boolean owns(UUID id, Reward reward) {
            return flags.getOrDefault(id, EnumSet.noneOf(Reward.class)).contains(reward);
        }
        boolean isHealthy() { return !readFailed; }
        boolean grant(UUID id, Reward reward) {
            if (readFailed) return false;
            if (!flags.computeIfAbsent(id, x -> EnumSet.noneOf(Reward.class)).add(reward)) return false;
            save();
            return true;
        }
        boolean flightOn(UUID id) { return flight.getOrDefault(id, false); }
        void setFlight(UUID id, boolean on) { flight.put(id, on); save(); }
        void save() {
            if (readFailed) return;
            JsonObject data = new JsonObject();
            for (var entry : flags.entrySet()) {
                JsonObject record = new JsonObject();
                var rewards = new com.google.gson.JsonArray();
                for (Reward r : entry.getValue()) rewards.add(r.name());
                record.add("rewards", rewards);
                record.addProperty("flight", flightOn(entry.getKey()));
                data.add(entry.getKey().toString(), record);
            }
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                LOGGER.error("Saving rewards failed: {}", file, ex);
            }
        }
    }
}
