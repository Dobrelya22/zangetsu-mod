package com.zangetsu.shots;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.logic.Awakening;
import com.zangetsu.logic.Bond;
import com.zangetsu.logic.Dream;
import com.zangetsu.logic.Execution;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.logic.Limbs;
import com.zangetsu.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/**
 * Проверочная «съёмка» мода: игра сама создаёт плоский мир, проходит все сцены (пробуждение, глаз,
 * стрелы, руки зомби, казнь, колени эндермена, форма Пустого, луна, Гецуга, сон с мечами) и делает
 * скриншоты в run-shots/screenshots. Включается флагом -Dzangetsu.shots=true (./gradlew runShots),
 * в jar мода не попадает.
 */
public class ZangetsuShots implements ClientModInitializer {
    private static final boolean ENABLED = Boolean.getBoolean("zangetsu.shots");
    private static final int WARMUP = 100;
    private static final float TICK_RATE = 10.0F;

    private record Step(int tick, Consumer<MinecraftClient> action) {
    }

    private final List<Step> script = new ArrayList<>();
    private boolean worldRequested;
    private int warmup;
    private int tick = -1;
    private int end;
    private int shotIndex;
    private LivingEntity zombie;
    private LivingEntity enderman;

    @Override
    public void onInitializeClient() {
        if (!ENABLED) {
            return;
        }
        this.write();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void at(int tick, Consumer<MinecraftClient> action) {
        this.script.add(new Step(tick, action));
        this.end = Math.max(this.end, tick + 20);
    }

    private void write() {
        // Пробуждение днём.
        at(0, c -> {
            command(c, "time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false",
                    "gamerule sendCommandFeedback false", "tp @p 0.5 -60 0.5 180 0", String.format(Locale.ROOT, "tick rate %.1f", TICK_RATE));
            look(c, 180.0F, 0.0F);
        });
        at(12, c -> shot(c, "start"));
        at(20, c -> server(c, s -> {
            ServerPlayerEntity player = player(s);
            player.setHealth(2.0F);
            Awakening.start(player);
        }));
        at(34, c -> shot(c, "spirit_appears"));
        at(56, c -> shot(c, "spirit_windup"));
        at(68, c -> shot(c, "sword_thrown"));
        at(78, c -> shot(c, "sword_flying"));
        at(88, c -> shot(c, "sword_falling"));
        at(102, c -> shot(c, "sword_stuck"));
        at(106, c -> server(c, s -> {
            ServerPlayerEntity player = player(s);
            ThrownZangetsuEntity sword = nearest(player.getServerWorld(), ThrownZangetsuEntity.class, player.getPos(), 12.0);
            if (sword != null) {
                Awakening.pickUp(player, sword);
            } else {
                ZangetsuMod.LOGGER.warn("Съёмка: меч не найден");
            }
        }));
        at(114, c -> shot(c, "touch_reach"));
        at(126, c -> shot(c, "touch_flash"));
        at(134, c -> shot(c, "floating_sword"));
        at(162, c -> shot(c, "bonded_hud_day"));
        at(166, c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
        at(172, c -> shot(c, "third_person_day"));
        at(176, c -> c.options.setPerspective(Perspective.FIRST_PERSON));

        // Ночь: глаз открывается, стрелы рассекаются.
        at(180, c -> command(c, "time set 18000"));
        at(192, c -> shot(c, "night_eye_open"));
        at(196, c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
        at(202, c -> shot(c, "night_third_person"));
        at(206, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
        at(210, c -> server(c, s -> Bond.change(player(s), Bond.THRESHOLDS[1] - ModAttachments.data(player(s)).bond)));
        at(220, c -> shot(c, "level_up"));
        at(240, c -> server(c, s -> shootArrows(player(s))));
        at(243, c -> shot(c, "arrows_parry_1"));
        at(246, c -> shot(c, "arrows_parry_2"));
        at(252, c -> shot(c, "arrow_halves"));

        // Зомби: левая рука, правая рука, казнь.
        at(270, c -> server(c, s -> this.zombie = summon(player(s), EntityType.ZOMBIE, 2.3)));
        at(280, c -> server(c, s -> Limbs.cut(this.zombie, player(s), Limbs.LEFT_ARM)));
        at(290, c -> shot(c, "zombie_left_arm"));
        at(298, c -> server(c, s -> Limbs.cut(this.zombie, player(s), Limbs.RIGHT_ARM)));
        at(308, c -> shot(c, "zombie_no_arms_prompt"));
        at(314, c -> server(c, s -> Execution.request(player(s), this.zombie.getId())));
        at(322, c -> shot(c, "execute_windup"));
        at(328, c -> shot(c, "execute_strike"));
        at(333, c -> shot(c, "execute_head_off"));
        at(342, c -> shot(c, "execute_after"));

        // Эндермен: левое колено, правое колено, рассечение пополам.
        at(370, c -> server(c, s -> this.enderman = summon(player(s), EntityType.ENDERMAN, 3.2)));
        at(380, c -> server(c, s -> Limbs.cut(this.enderman, player(s), Limbs.LEFT_KNEE)));
        at(390, c -> shot(c, "ender_one_knee"));
        at(398, c -> server(c, s -> Limbs.cut(this.enderman, player(s), Limbs.RIGHT_KNEE)));
        at(410, c -> shot(c, "ender_kneeling"));
        at(416, c -> server(c, s -> Execution.request(player(s), this.enderman.getId())));
        at(431, c -> shot(c, "ender_slash"));
        at(436, c -> shot(c, "ender_split"));
        at(446, c -> shot(c, "ender_halves"));

        // Форма Пустого, луна, Гецуга.
        at(470, c -> server(c, s -> {
            ServerPlayerEntity player = player(s);
            Bond.change(player, Bond.THRESHOLDS[3] - ModAttachments.data(player).bond);
            ModAttachments.data(player).hollowUsed = 0;
        }));
        at(490, c -> server(c, s -> HollowForm.toggle(player(s))));
        at(498, c -> shot(c, "hollow_transform"));
        at(510, c -> shot(c, "hollow_roar"));
        at(530, c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
        at(536, c -> shot(c, "hollow_mask"));
        at(540, c -> {
            c.options.setPerspective(Perspective.FIRST_PERSON);
            look(c, -150.0F, -48.0F);
        });
        at(548, c -> shot(c, "crescent_moon"));
        at(552, c -> look(c, 180.0F, 5.0F));
        at(556, c -> server(c, s -> HollowForm.tryGetsuga(player(s))));
        at(559, c -> shot(c, "getsuga_1"));
        at(563, c -> shot(c, "getsuga_2"));
        at(580, c -> server(c, s -> HollowForm.end(player(s), false)));
        at(584, c -> shot(c, "mask_shatter"));

        // Сон с мечами.
        at(600, c -> server(c, s -> Dream.forceBegin(player(s))));
        at(630, c -> {
            look(c, 90.0F, 12.0F);
            shot(c, "dream_field");
        });
        at(636, c -> {
            look(c, 0.0F, 20.0F);
            shot(c, "dream_field_2");
        });
        at(642, c -> server(c, s -> {
            ServerPlayerEntity player = player(s);
            ThrownZangetsuEntity sword = nearest(player.getServerWorld(), ThrownZangetsuEntity.class, player.getPos(), 20.0);
            if (sword != null) {
                Dream.pull(player, sword);
            }
        }));
        at(652, c -> shot(c, "dream_reach"));
        at(664, c -> shot(c, "dream_rise"));
        at(680, c -> shot(c, "dream_white"));
        at(708, c -> shot(c, "woke_up"));
        at(725, c -> {
            c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        });
        at(730, c -> shot(c, "woke_up_third_person"));
    }

    // ---------------------------------------------------------------- действия

    private static void shootArrows(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d target = player.getEyePos().add(0, -0.4, 0);
        for (int i = 0; i < 3; i++) {
            Vec3d from = player.getPos().add(-1.5 + i * 1.5, 1.8, -9.0);
            ArrowEntity arrow = new ArrowEntity(world, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
            Vec3d d = target.subtract(from);
            arrow.setVelocity(d.x, d.y + 0.15, d.z, 1.6F, 0.0F);
            world.spawnEntity(arrow);
        }
    }

    private static LivingEntity summon(ServerPlayerEntity player, EntityType<? extends MobEntity> type, double distance) {
        ServerWorld world = player.getServerWorld();
        MobEntity mob = type.create(world);
        if (mob == null) {
            return null;
        }
        Vec3d at = player.getPos().add(0, 0, -distance);
        mob.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
        mob.setHeadYaw(0.0F);
        mob.setBodyYaw(0.0F);
        mob.setAiDisabled(true);
        mob.setPersistent();
        mob.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(at)), SpawnReason.COMMAND, null);
        mob.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        world.spawnEntity(mob);
        return mob;
    }

    private static <T extends Entity> T nearest(ServerWorld world, Class<T> type, Vec3d around, double radius) {
        return world.getEntitiesByClass(type, new net.minecraft.util.math.Box(around, around).expand(radius), e -> true).stream()
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(around))).orElse(null);
    }

    // ---------------------------------------------------------------- тик

    private void onTick(MinecraftClient client) {
        if (!this.worldRequested && client.currentScreen instanceof TitleScreen) {
            this.worldRequested = true;
            createWorld(client);
            return;
        }
        if (client.world == null || client.player == null || client.getServer() == null) {
            return;
        }
        if (this.warmup < WARMUP) {
            this.warmup++;
            client.getToastManager().clear();
            return;
        }
        this.tick++;
        for (Step step : this.script) {
            if (step.tick() == this.tick) {
                try {
                    step.action().accept(client);
                } catch (RuntimeException e) {
                    ZangetsuMod.LOGGER.error("Съёмка: шаг {} упал", this.tick, e);
                }
            }
        }
        if (this.tick % 50 == 0) {
            ZangetsuMod.LOGGER.info("Съёмка: тик {} из {}, {} кадров/с", this.tick, this.end, client.getCurrentFps());
        }
        if (this.tick >= this.end) {
            ZangetsuMod.LOGGER.info("Съёмка: готово, {} кадров", this.shotIndex);
            client.scheduleStop();
        }
    }

    private static void createWorld(MinecraftClient client) {
        GameRules rules = new GameRules();
        rules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
        rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        rules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
        rules.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, null);
        rules.get(GameRules.SEND_COMMAND_FEEDBACK).set(false, null);
        rules.get(GameRules.LOG_ADMIN_COMMANDS).set(false, null);
        LevelInfo info = new LevelInfo("zangetsu-shots", GameMode.SURVIVAL, false, Difficulty.EASY, true, rules, DataConfiguration.SAFE_MODE);
        client.createIntegratedServerLoader().createAndStart("zangetsu-shots", info,
                new GeneratorOptions(20261026L, false, false),
                registries -> registries.getWrapperOrThrow(RegistryKeys.WORLD_PRESET)
                        .getOrThrow(WorldPresets.FLAT).value().createDimensionsRegistryHolder(),
                client.currentScreen);
    }

    private void shot(MinecraftClient client, String name) {
        String file = String.format(Locale.ROOT, "%02d_%s.png", this.shotIndex++, name);
        ScreenshotRecorder.saveScreenshot(client.runDirectory, file, client.getFramebuffer(), text -> {
        });
        ZangetsuMod.LOGGER.info("Съёмка: кадр {}", file);
    }

    private static void look(MinecraftClient client, float yaw, float pitch) {
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
        client.player.prevYaw = yaw;
        client.player.prevPitch = pitch;
        client.player.setHeadYaw(yaw);
        client.player.setBodyYaw(yaw);
    }

    private static ServerPlayerEntity player(MinecraftServer server) {
        return server.getPlayerManager().getPlayerList().get(0);
    }

    private static void server(MinecraftClient client, Consumer<MinecraftServer> action) {
        MinecraftServer server = client.getServer();
        if (server != null) {
            server.execute(() -> {
                try {
                    action.accept(server);
                } catch (RuntimeException e) {
                    ZangetsuMod.LOGGER.error("Съёмка: действие на сервере упало", e);
                }
            });
        }
    }

    private static void command(MinecraftClient client, String... commands) {
        server(client, server -> {
            for (String command : commands) {
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
            }
        });
    }
}
