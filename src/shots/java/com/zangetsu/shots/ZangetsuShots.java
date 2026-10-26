package com.zangetsu.shots;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.logic.Awakening;
import com.zangetsu.logic.Bond;
import com.zangetsu.logic.Dream;
import com.zangetsu.logic.Execution;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.logic.Limbs;
import com.zangetsu.logic.SwordStacks;
import com.zangetsu.registry.ModAttachments;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.slot.Slot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import com.zangetsu.client.CameraDirector;
import com.zangetsu.client.ClientState;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
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
 *
 * <p>Сценарий — очередь шагов. Паузы отсчитываются по тикам сервера (в CI он может отставать от
 * клиента), а там, где важен момент (меч вонзился, игрок во сне), шаг ждёт само событие.
 */
public class ZangetsuShots implements ClientModInitializer {
    private static final boolean ENABLED = Boolean.getBoolean("zangetsu.shots");
    private static final int WARMUP = 100;
    /** Сколько тиков сервера ждать условие, прежде чем сдаться и идти дальше. */
    private static final int WAIT_LIMIT = 400;

    /** Шаг сценария: вернул false — шаг ещё не готов, повторить на следующем тике. */
    private interface Action {
        boolean run(MinecraftClient client);
    }

    private static final AtomicLong SERVER_TICKS = new AtomicLong();

    private final List<Action> queue = new ArrayList<>();
    private int index;
    private long resumeAt;
    private long waitStarted = -1;
    private boolean worldRequested;
    private int warmup;
    private int shotIndex;
    private LivingEntity zombie;
    private LivingEntity enderman;
    private LivingEntity attacker;
    private float healthBefore;
    private int bondBefore;
    private int checksTotal;
    private int checksPassed;
    private final List<String> failed = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        if (!ENABLED) {
            return;
        }
        this.write();
        ServerTickEvents.END_SERVER_TICK.register(server -> SERVER_TICKS.incrementAndGet());
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    // ---------------------------------------------------------------- построение сценария

    private void then(Action action) {
        this.queue.add(action);
    }

    private void act(Consumer<MinecraftClient> action) {
        this.queue.add(c -> {
            action.accept(c);
            return true;
        });
    }

    private void pause(int serverTicks) {
        this.queue.add(c -> {
            this.resumeAt = SERVER_TICKS.get() + serverTicks;
            return true;
        });
    }

    private void waitFor(String what, Predicate<MinecraftClient> condition) {
        this.queue.add(c -> {
            if (this.waitStarted < 0) {
                this.waitStarted = SERVER_TICKS.get();
            }
            boolean ok = condition.test(c);
            if (!ok && SERVER_TICKS.get() - this.waitStarted < WAIT_LIMIT) {
                return false;
            }
            if (!ok) {
                ZangetsuMod.LOGGER.warn("Съёмка: не дождались «{}»", what);
            }
            this.waitStarted = -1;
            return true;
        });
    }

    private void shot(String name) {
        act(c -> {
            String file = String.format(Locale.ROOT, "%02d_%s.png", this.shotIndex++, name);
            ScreenshotRecorder.saveScreenshot(c.runDirectory, file, c.getFramebuffer(), text -> {
            });
            ZangetsuMod.LOGGER.info("Съёмка: кадр {}", file);
        });
    }

    private void onServer(Consumer<MinecraftServer> action) {
        act(c -> server(c, action));
    }

    private void command(String... commands) {
        onServer(s -> {
            for (String command : commands) {
                s.getCommandManager().executeWithPrefix(s.getCommandSource(), command);
            }
        });
    }

    private void view(Perspective perspective) {
        act(c -> c.options.setPerspective(perspective));
    }

    private void look(float yaw, float pitch) {
        act(c -> look(c, yaw, pitch));
    }

    private void write() {
        // ---- Пробуждение днём.
        command("time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false",
                "tp @p 0.5 -60 0.5 180 0");
        look(180.0F, 0.0F);
        pause(20);
        shot("start");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            player.setHealth(2.0F);
            Awakening.start(player);
        });
        pause(14);
        shot("spirit_appears");
        pause(30);
        shot("spirit_hold");
        pause(16);
        shot("spirit_windup");
        pause(6);
        shot("spirit_throw");
        pause(7);
        shot("sword_flying_1");
        pause(9);
        shot("sword_flying_2");
        waitFor("меч вонзился", c -> stuckSword(c) != null);
        act(c -> {
            ThrownZangetsuEntity sword = stuckSword(c);
            if (sword != null) {
                lookAt(c, sword.getPos().add(0.0, 0.9, 0.0));
            }
        });
        pause(6);
        shot("sword_stuck");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            ThrownZangetsuEntity sword = nearest(player.getServerWorld(), ThrownZangetsuEntity.class, player.getPos(), 16.0);
            if (sword != null) {
                Awakening.pickUp(player, sword);
            }
        });
        pause(10);
        shot("touch_reach");
        pause(13);
        shot("touch_flash");
        pause(6);
        shot("floating_sword");
        waitFor("меч в руке", c -> com.zangetsu.logic.SwordStacks.isHolding(c.player));
        pause(30);
        look(180.0F, 10.0F);
        pause(4);
        shot("bonded_hud_day");
        view(Perspective.THIRD_PERSON_FRONT);
        pause(6);
        shot("third_person_day");
        view(Perspective.FIRST_PERSON);
        // Сбоку от третьего лица — лезвие должно смотреть вниз.
        act(c -> CameraDirector.begin(40, new CameraDirector.Shot(90.0F, 90.0F, 6.0F, 2.6F, 0.0F)));
        pause(6);
        shot("sword_side_3p");
        act(c -> CameraDirector.begin(40, new CameraDirector.Shot(-90.0F, -90.0F, 6.0F, 2.6F, 0.0F)));
        pause(6);
        shot("sword_side_3p_other");
        pause(40);
        act(c -> c.setScreen(new InventoryScreen(c.player)));
        pause(10);
        shot("inventory");
        act(c -> c.setScreen(null));
        pause(4);

        // ---- Ночь: глаз открывается, стрелы рассекаются.
        command("time set 18000");
        pause(16);
        shot("night_eye_open");
        view(Perspective.THIRD_PERSON_FRONT);
        pause(6);
        shot("night_third_person");
        view(Perspective.FIRST_PERSON);
        onServer(s -> Bond.change(player(s), Bond.THRESHOLDS[1] - ModAttachments.data(player(s)).bond));
        pause(12);
        shot("level_up");
        look(180.0F, 0.0F);
        pause(30);
        onServer(s -> shootArrows(player(s)));
        pause(3);
        shot("arrows_parry_1");
        pause(3);
        shot("arrows_parry_2");
        pause(6);
        shot("arrow_halves");

        // ---- Зомби: левая рука, правая рука, казнь.
        onServer(s -> this.zombie = summon(player(s), EntityType.ZOMBIE, 2.3));
        look(180.0F, 12.0F);
        pause(12);
        shot("zombie");
        look(160.0F, 20.0F);
        pause(8);
        shot("eye_looks_at_zombie");
        look(180.0F, 12.0F);
        pause(4);
        onServer(s -> Limbs.cut(this.zombie, player(s), Limbs.LEFT_ARM));
        pause(8);
        shot("zombie_left_arm");
        onServer(s -> Limbs.cut(this.zombie, player(s), Limbs.RIGHT_ARM));
        pause(12);
        shot("zombie_no_arms_prompt");
        onServer(s -> Execution.request(player(s), this.zombie.getId()));
        pause(8);
        shot("execute_windup");
        pause(6);
        shot("execute_strike");
        pause(4);
        shot("execute_head_off");
        pause(10);
        shot("execute_after");
        pause(20);

        // ---- Эндермен: левое колено, правое колено, рассечение пополам.
        onServer(s -> this.enderman = summon(player(s), EntityType.ENDERMAN, 3.4));
        look(180.0F, -8.0F);
        pause(12);
        shot("enderman");
        onServer(s -> Limbs.cut(this.enderman, player(s), Limbs.LEFT_KNEE));
        pause(8);
        shot("ender_one_knee");
        onServer(s -> Limbs.cut(this.enderman, player(s), Limbs.RIGHT_KNEE));
        look(180.0F, 12.0F);
        pause(12);
        shot("ender_kneeling");
        onServer(s -> Execution.request(player(s), this.enderman.getId()));
        pause(13);
        shot("ender_slash");
        pause(4);
        shot("ender_split");
        pause(10);
        shot("ender_halves");
        pause(30);

        // ---- Проверки в настоящем бою (не постановка).
        look(180.0F, 10.0F);
        onServer(s -> this.attacker = summonHostile(player(s), EntityType.ZOMBIE, 1.8));
        pause(90);
        shot("check_melee_parry");
        onServer(s -> {
            check("удар зомби парируется и рука отлетает", ModAttachments.limbs(this.attacker) != 0);
            this.attacker.discard();
        });
        onServer(s -> Bond.change(player(s), Bond.THRESHOLDS[2] - ModAttachments.data(player(s)).bond));
        pause(20);
        onServer(s -> this.attacker = summonHostile(player(s), EntityType.ZOMBIE, 2.6));
        pause(70);
        shot("check_auto_attack");
        onServer(s -> {
            check("на III уровне меч сам бьёт зомби", !this.attacker.isAlive() || this.attacker.getHealth() < this.attacker.getMaxHealth());
            this.attacker.discard();
        });
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            this.healthBefore = player.getHealth();
            this.attacker = summonHostile(player, EntityType.SKELETON, 9.0);
            this.attacker.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        });
        pause(120);
        shot("check_skeleton");
        onServer(s -> {
            check("стрелы скелета не ранят (рассечены)", player(s).getHealth() >= this.healthBefore);
            this.attacker.discard();
        });
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            this.bondBefore = ModAttachments.data(player).bond;
            LivingEntity cow = summon(player, EntityType.COW, 2.0);
            cow.damage(player.getServerWorld().getDamageSources().playerAttack(player), 1000.0F);
        });
        pause(5);
        onServer(s -> check("убийство коровы огорчает меч", ModAttachments.data(player(s)).bond < this.bondBefore));
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            player.dropSelectedItem(true);
            check("Q не выбрасывает меч", SwordStacks.isHolding(player));
            check("меч не кладётся в сундук", !new Slot(new SimpleInventory(1), 0, 0, 0).canInsert(SwordStacks.held(player)));
        });
        onServer(s -> player(s).kill());
        waitFor("смерть", c -> c.player.isDead());
        pause(10);
        act(c -> c.player.requestRespawn());
        waitFor("возрождение", c -> c.player.isAlive() && !c.player.isDead());
        pause(30);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            check("меч остаётся после смерти", !SwordStacks.find(player).isEmpty());
            check("+10 здоровья остаются после смерти", player.getMaxHealth() >= 30.0F);
        });
        shot("check_after_death");
        command("tp @p 0.5 -60 0.5 180 0");
        pause(10);

        // ---- Форма Пустого, луна, Гецуга.
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            Bond.change(player, Bond.THRESHOLDS[3] - ModAttachments.data(player).bond);
            ModAttachments.data(player).hollowUsed = 0;
        });
        pause(40);
        onServer(s -> HollowForm.toggle(player(s)));
        pause(8);
        shot("hollow_transform");
        pause(12);
        shot("hollow_roar");
        pause(20);
        view(Perspective.THIRD_PERSON_FRONT);
        pause(6);
        shot("hollow_mask");
        view(Perspective.THIRD_PERSON_BACK);
        pause(4);
        shot("hollow_back");
        view(Perspective.FIRST_PERSON);
        look(-150.0F, -48.0F);
        pause(10);
        shot("crescent_moon");
        look(0.0F, -88.0F);
        pause(4);
        shot("sky_overhead_no_vanilla_moon");
        look(180.0F, -12.0F);
        pause(4);
        shot("blood_sky");
        look(180.0F, 5.0F);
        pause(4);
        onServer(s -> HollowForm.tryGetsuga(player(s)));
        pause(3);
        shot("getsuga_1");
        pause(4);
        shot("getsuga_2");
        pause(30);
        // Гецуга сбоку и сверху — видно, что серп лежит горизонтально.
        act(c -> CameraDirector.begin(60, new CameraDirector.Shot(95.0F, 95.0F, 32.0F, 7.0F, 3.0F)));
        pause(2);
        onServer(s -> HollowForm.tryGetsuga(player(s)));
        pause(5);
        shot("getsuga_side");
        pause(60);

        // Маска трескается: 30 с — трещина, 15 с — скол, 5 с — почти рассыпалась, потом ломается.
        view(Perspective.THIRD_PERSON_FRONT);
        onServer(s -> ModAttachments.data(player(s)).hollowUsed = HollowForm.BUDGET - 31 * 20);
        pause(30);
        shot("mask_crack1");
        onServer(s -> {
            check("на 30 секундах маска треснула", HollowForm.crack(player(s)) == 1);
            ModAttachments.data(player(s)).hollowUsed = HollowForm.BUDGET - 16 * 20;
        });
        pause(30);
        shot("mask_crack2");
        onServer(s -> ModAttachments.data(player(s)).hollowUsed = HollowForm.BUDGET - 6 * 20);
        pause(30);
        shot("mask_crack3");
        view(Perspective.FIRST_PERSON);
        pause(4);
        shot("mask_crack3_hud");
        waitFor("маска сломалась", c -> !ClientState.isHollow(c.player));
        pause(6);
        shot("mask_shatter");
        onServer(s -> check("через 2 минуты маска ломается сама", !ModAttachments.data(player(s)).hollowActive));
        pause(20);
        shot("mask_hud_tomorrow");

        // ---- Сон с мечами.
        onServer(s -> Dream.forceBegin(player(s)));
        waitFor("сон", c -> c.world != null && c.world.getRegistryKey() == Dream.WORLD);
        pause(40);
        look(90.0F, 14.0F);
        pause(3);
        shot("dream_field");
        look(0.0F, 22.0F);
        pause(3);
        shot("dream_field_2");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            double x = player.getX() + 70.0, z = player.getZ() + 10.0;
            double y = player.getServerWorld().getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.ofFloored(x, 0, z)).getY();
            player.networkHandler.requestTeleport(x, y, z, 90.0F, 10.0F);
        });
        pause(60);
        look(-90.0F, 8.0F);
        pause(3);
        shot("dream_far");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            long swords = player.getServerWorld().getEntitiesByClass(ThrownZangetsuEntity.class, player.getBoundingBox().expand(40.0, 30.0, 40.0),
                    e -> e.isDream()).size();
            ZangetsuMod.LOGGER.info("Съёмка: мечей вокруг во сне после пробежки: {}", swords);
            check("во сне вокруг много мечей и после пробежки", swords >= 60);
        });
        look(90.0F, 8.0F);
        pause(3);
        shot("dream_far_back");
        act(c -> {
            ThrownZangetsuEntity sword = nearestClient(c);
            if (sword != null) {
                lookAt(c, sword.getPos().add(0.0, 0.9, 0.0));
            }
        });
        pause(4);
        shot("dream_sword_close");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            ThrownZangetsuEntity sword = nearest(player.getServerWorld(), ThrownZangetsuEntity.class, player.getPos(), 24.0);
            if (sword != null) {
                Dream.pull(player, sword);
            }
        });
        pause(10);
        shot("dream_reach");
        pause(14);
        shot("dream_rise");
        pause(16);
        shot("dream_white");
        waitFor("пробуждение", c -> c.world != null && c.world.getRegistryKey() != Dream.WORLD);
        pause(30);
        shot("woke_up");
        view(Perspective.THIRD_PERSON_FRONT);
        pause(6);
        shot("woke_up_third_person");
        onServer(s -> check("после сна меч снова в руке", SwordStacks.isHolding(player(s))));
        pause(5);
        act(c -> {
            ZangetsuMod.LOGGER.info("Съёмка: готово, {} кадров", this.shotIndex);
            ZangetsuMod.LOGGER.info("ПРОВЕРКИ: пройдено {} из {}{}", this.checksPassed, this.checksTotal,
                    this.failed.isEmpty() ? "" : ", провалены: " + String.join("; ", this.failed));
            c.scheduleStop();
        });
    }

    /** Проверка поведения (выполняется на сервере); итог — в журнале строкой «ПРОВЕРКИ: …». */
    private void check(String name, boolean ok) {
        this.checksTotal++;
        if (ok) {
            this.checksPassed++;
        } else {
            this.failed.add(name);
        }
        ZangetsuMod.LOGGER.info("ПРОВЕРКА {} {}", ok ? "✔" : "✘", name);
    }

    /** Злой моб с включённым ИИ — нападает по-настоящему. */
    private static LivingEntity summonHostile(ServerPlayerEntity player, EntityType<? extends MobEntity> type, double distance) {
        ServerWorld world = player.getServerWorld();
        MobEntity mob = type.create(world);
        if (mob == null) {
            return null;
        }
        Vec3d at = player.getPos().add(0, 0, -distance);
        mob.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
        mob.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(at)), SpawnReason.COMMAND, null);
        mob.setBaby(false);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            mob.equipStack(slot, ItemStack.EMPTY);
        }
        mob.setTarget(player);
        world.spawnEntity(mob);
        return mob;
    }

    // ---------------------------------------------------------------- действия на сервере

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
        mob.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(at)), SpawnReason.COMMAND, null);
        mob.setHeadYaw(0.0F);
        mob.setBodyYaw(0.0F);
        mob.setAiDisabled(true);
        mob.setPersistent();
        mob.setBaby(false);
        mob.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            mob.equipStack(slot, ItemStack.EMPTY);
        }
        world.spawnEntity(mob);
        return mob;
    }

    private static <T extends Entity> T nearest(ServerWorld world, Class<T> type, Vec3d around, double radius) {
        return world.getEntitiesByClass(type, new Box(around, around).expand(radius), e -> true).stream()
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(around))).orElse(null);
    }

    private static ThrownZangetsuEntity stuckSword(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return null;
        }
        return client.world.getEntitiesByClass(ThrownZangetsuEntity.class, client.player.getBoundingBox().expand(16.0),
                s -> s.getState() == ThrownZangetsuEntity.STUCK).stream().findFirst().orElse(null);
    }

    private static ThrownZangetsuEntity nearestClient(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return null;
        }
        Vec3d at = client.player.getPos();
        return client.world.getEntitiesByClass(ThrownZangetsuEntity.class, client.player.getBoundingBox().expand(24.0), s -> true)
                .stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(at))).orElse(null);
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
        while (this.index < this.queue.size() && SERVER_TICKS.get() >= this.resumeAt) {
            Action action = this.queue.get(this.index);
            boolean done;
            try {
                done = action.run(client);
            } catch (RuntimeException e) {
                ZangetsuMod.LOGGER.error("Съёмка: шаг {} упал", this.index, e);
                done = true;
            }
            if (!done) {
                return;
            }
            this.index++;
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

    private static void look(MinecraftClient client, float yaw, float pitch) {
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
        client.player.prevYaw = yaw;
        client.player.prevPitch = pitch;
        client.player.setHeadYaw(yaw);
        client.player.setBodyYaw(yaw);
    }

    private static void lookAt(MinecraftClient client, Vec3d target) {
        Vec3d eye = client.player.getEyePos();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
        look(client, yaw, pitch);
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
}
