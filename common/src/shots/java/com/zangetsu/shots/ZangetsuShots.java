package com.zangetsu.shots;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.client.KeyBindings;
import com.zangetsu.entity.GetsugaEntity;
import com.zangetsu.entity.SeveredPartEntity;
import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.logic.Awakening;
import com.zangetsu.logic.Bond;
import com.zangetsu.logic.Calling;
import com.zangetsu.logic.Cinematic;
import com.zangetsu.logic.Dream;
import com.zangetsu.logic.Execution;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.logic.Limbs;
import com.zangetsu.logic.Reaction;
import com.zangetsu.logic.SwordBreak;
import com.zangetsu.logic.SwordStacks;
import com.zangetsu.logic.Trial;
import com.zangetsu.logic.ZangetsuData;
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
import net.minecraft.client.MinecraftClient;
import com.zangetsu.client.CameraDirector;
import com.zangetsu.client.ClientState;
import com.zangetsu.client.ZangetsuCombat;
import com.zangetsu.client.ReactionCheck;
import com.zangetsu.client.render.FormPhysics;
import com.zangetsu.client.render.PlayerPoses;
import com.zangetsu.network.AnimationPayload;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
import net.minecraft.block.Blocks;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
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
public class ZangetsuShots {
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
    private MobEntity golem;
    private float healthBefore;
    private int bondBefore;
    private int checksTotal;
    private int checksPassed;
    private final List<String> failed = new ArrayList<>();

    /** Съёмка включена ключом -Dzangetsu.shots=true; без него мод съёмки ничего не делает. */
    public static boolean enabled() {
        return ENABLED;
    }

    /**
     * Загрузчик (Fabric или NeoForge) создаёт съёмку и зовёт {@link #serverTick()} в конце тика сервера,
     * {@link #clientTick} в конце тика клиента и {@link #frame} после отрисовки мира.
     */
    public ZangetsuShots() {
        this.write();
    }

    public static void serverTick() {
        SERVER_TICKS.incrementAndGet();
    }

    public void clientTick(MinecraftClient client) {
        this.onTick(client);
    }

    public void frame(MinecraftClient client) {
        this.onFrame(client);
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
        // В основной части мобов убивают напрямую, проверка реакции идёт отдельным разделом.
        Reaction.enabled = false;
        // Только модель Яхве со всех сторон.
        if ("model".equals(System.getProperty("zangetsu.shots.only"))) {
            writeModel();
            this.writeEnd();
            return;
        }
        // Только сцена, где меч ломают, и поиск настоящего.
        if ("break".equals(System.getProperty("zangetsu.shots.only"))) {
            command("time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "tp @p 0.5 -60 0.5 180 0", "execute as @p run zangetsu give");
            pause(20);
            writeBreak();
            this.writeEnd();
            return;
        }
        // Только сцена, где Пустой забирает тело. В тесноте (под крышей) она должна уйти на отдельное поле и вернуться обратно.
        if ("rampage".equals(System.getProperty("zangetsu.shots.only")) || "rampage_cave".equals(System.getProperty("zangetsu.shots.only"))) {
            command("time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "tp @p 0.5 -60 0.5 180 0");
            if ("rampage_cave".equals(System.getProperty("zangetsu.shots.only"))) {
                command("fill -3 -57 -3 3 -57 3 minecraft:stone");
                this.expectArena = true;
            }
            pause(20);
            writeRampage();
            this.writeEnd();
            return;
        }
        // Только разрубы: каждый новый приём покадрово.
        if ("cuts".equals(System.getProperty("zangetsu.shots.only"))) {
            command("time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "tp @p 0.5 -60 0.5 180 0");
            pause(20);
            writeCuts();
            writeGauge();
            this.writeEnd();
            return;
        }
        // ---- Первый сон: сначала отказ, потом меч.
        writeFirstDream();
        if ("calling".equals(System.getProperty("zangetsu.shots.only"))) {
            this.writeEnd();
            return;
        }
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
        pause(6);
        shot("claim_walk");
        waitFor("подошёл к мечу", c -> {
            ClientState.Animation scene = ClientState.animation(c.player);
            return scene != null && c.player.age - scene.startAge() >= scene.duration() - Awakening.CLAIM_TICKS + 12;
        });
        act(this::checkClaimSmooth);
        waitFor("меч в руке", c -> com.zangetsu.logic.SwordStacks.isHolding(c.player));
        shot("claim_grip");
        pause(18);
        shot("claim_pull");
        pause(14);
        shot("claim_arc");
        pause(26);
        shot("claim_shoulder");
        pause(16);
        shot("claim_look");
        pause(30);
        shot("claim_proud");
        waitFor("сцена подбора кончилась", c -> ClientState.animation(c.player) == null);
        pause(40);
        shot("calling_wake_up");
        waitFor("проснулся с мечом", c -> c.world != null && c.world.getRegistryKey() == World.OVERWORLD);
        pause(12);
        shot("calling_morning");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            check("взял меч: проснулся с ним там, где лёг", !Calling.inCalling(player) && !Calling.isActive(player.getUuid()) && ModAttachments.data(player).bonded
                    && ModAttachments.data(player).dream == null && SwordStacks.isHolding(player) && player.getWorld().isDay());
            check("взял меч: режим игры прежний", player.interactionManager.getGameMode() != GameMode.ADVENTURE);
        });
        command("time set 6000", "setblock 3 -60 0 air", "setblock 2 -60 0 air", "tp @p 0.5 -60 0.5 180 0");
        pause(6);
        onServer(s -> check("сцена подбора кончилась, игрок свободен", !Cinematic.isLocked(player(s)) && !Awakening.isActive(player(s).getUuid())));
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
        act(c -> c.player.swingHand(net.minecraft.util.Hand.MAIN_HAND));
        pause(2);
        shot("first_person_swing");
        pause(10);
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

        // ---- Казнь без отсечений: целого нельзя, едва живого можно. Конечностей у крипера нет, так что только так.
        onServer(s -> {
            this.attacker = summon(player(s), EntityType.CREEPER, 3.0);
            Execution.request(player(s), this.attacker.getId());
        });
        pause(40);
        onServer(s -> {
            check("здорового крипера без финальной формы казнить нельзя", this.attacker.isAlive());
            this.attacker.setHealth(this.attacker.getMaxHealth() * 0.3F);
            Execution.request(player(s), this.attacker.getId());
        });
        pause(60);
        onServer(s -> check("едва живого крипера можно казнить", !this.attacker.isAlive()));
        pause(10);

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
        // Удар по нажатию: урон не в миг клика, а когда клинок доходит до цели. Голем — чтобы меч не ударил его сам.
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            this.golem = EntityType.IRON_GOLEM.create(player.getServerWorld());
            Vec3d at = player.getPos().add(player.getRotationVector().multiply(2.2).multiply(1.0, 0.0, 1.0));
            this.golem.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
            this.golem.setAiDisabled(true);
            player.getServerWorld().spawnEntity(this.golem);
        });
        pause(6);
        act(c -> lookAt(c, this.golem.getBoundingBox().getCenter()));
        pause(4);
        act(c -> check("нажатие атаки начинает замах Зангецу", ZangetsuCombat.onAttackKey(c)));
        pause(2);
        onServer(s -> check("в миг клика урона ещё нет: идёт замах", this.golem.getHealth() >= this.golem.getMaxHealth()));
        pause(16);
        onServer(s -> {
            check("урон приходит, когда клинок дошёл до цели", this.golem.getHealth() < this.golem.getMaxHealth());
            this.golem.discard();
        });
        pause(10);
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
        shot("hud_ready");
        onServer(s -> HollowForm.toggle(player(s)));
        pause(16);
        shot("mask_hand_rises");
        pause(16);
        shot("mask_swipe_forehead");
        pause(10);
        shot("mask_swipe_chin");
        pause(14);
        shot("mask_roar");
        pause(28);
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
        // Замах: волна срывается с клинка через GETSUGA_WINDUP тиков.
        pause(HollowForm.GETSUGA_WINDUP + 3);
        shot("getsuga_1");
        pause(4);
        shot("getsuga_2");
        pause(30);
        // Гецуга сбоку и сверху — видно, что серп лежит горизонтально.
        act(c -> CameraDirector.begin(60, new CameraDirector.Shot(95.0F, 95.0F, 32.0F, 7.0F, 3.0F)));
        pause(2);
        onServer(s -> HollowForm.tryGetsuga(player(s)));
        pause(HollowForm.GETSUGA_WINDUP + 5);
        shot("getsuga_side");
        pause(60);

        // Половина силы: фронт горения посередине клинка.
        onServer(s -> ModAttachments.data(player(s)).hollowUsed = HollowForm.BUDGET / 2);
        pause(30);
        shot("hud_half");

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

        // ---- Финальная форма (уровень V): маска с одним рогом, эффекты сильнее, Гецуга крупнее.
        onServer(s -> {
            ModAttachments.data(player(s)).hollowUsed = 0;
            HollowForm.toggle(player(s));
        });
        pause(40);
        act(c -> check("на IV уровне маска обычная, без рога", ClientState.isHollow(c.player) && !ClientState.isFinal(c.player)));
        // Новый уровень прямо в форме Пустого: маска сразу становится финальной.
        onServer(s -> Bond.change(player(s), Bond.THRESHOLDS[4] - ModAttachments.data(player(s)).bond));
        // Заголовок нового уровня не должен закрывать маску на снимке.
        pause(90);
        view(Perspective.THIRD_PERSON_FRONT);
        look(180.0F, 5.0F);
        pause(6);
        shot("final_mask");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            check("на V уровне форма Пустого финальная", ModAttachments.data(player).hollowActive && HollowForm.isFinal(ModAttachments.data(player)));
            StatusEffectInstance strength = player.getStatusEffect(StatusEffects.STRENGTH);
            check("в финальной форме сила выше", strength != null && strength.getAmplifier() == 2);
        });
        act(c -> check("клиент рисует финальную маску", ClientState.isFinal(c.player)));
        view(Perspective.THIRD_PERSON_BACK);
        pause(4);
        onServer(s -> HollowForm.tryGetsuga(player(s)));
        pause(HollowForm.GETSUGA_WINDUP + 4);
        shot("final_getsuga");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            boolean big = !player.getServerWorld().getEntitiesByClass(GetsugaEntity.class, player.getBoundingBox().expand(30.0), g -> g.scale() > 1.0F).isEmpty();
            check("Гецуга финальной формы крупнее обычной", big);
        });
        pause(40);

        // Гецуга бьёт и тех, кто не нападает: пиглин мирный, пока на него не напали.
        look(180.0F, 2.0F);
        onServer(s -> this.attacker = summon(player(s), EntityType.PIGLIN, 7.0));
        pause(4);
        onServer(s -> HollowForm.tryGetsuga(player(s)));
        pause(HollowForm.GETSUGA_WINDUP + 24);
        onServer(s -> {
            check("Гецуга бьёт пиглина, даже мирного", !this.attacker.isAlive() || this.attacker.getHealth() < this.attacker.getMaxHealth());
            this.attacker.discard();
        });
        pause(10);

        // Финальная форма: целого моба можно казнить сразу и издалека, шаг сам выносит к нему.
        look(180.0F, 2.0F);
        onServer(s -> this.attacker = summon(player(s), EntityType.ZOMBIE, 11.0));
        pause(4);
        onServer(s -> Execution.request(player(s), this.attacker.getId()));
        pause(3);
        onServer(s -> check("шаг вынес игрока к мобу", player(s).distanceTo(this.attacker) < 4.5F));
        pause(30);
        onServer(s -> {
            check("в финальной форме целый моб казнён сразу", !this.attacker.isAlive());
            ServerPlayerEntity player = player(s);
            check("от моба остались куски", !player.getServerWorld().getEntitiesByClass(SeveredPartEntity.class, player.getBoundingBox().expand(12.0), p -> true).isEmpty());
        });
        // У каждого вида свой набор приёмов, и все они убивают; взрыв крипера никому не вредит.
        for (EntityType<? extends MobEntity> type : List.of(EntityType.SKELETON, EntityType.WITHER_SKELETON, EntityType.CREEPER, EntityType.PILLAGER,
                EntityType.VINDICATOR, EntityType.WITCH, EntityType.ENDERMAN, EntityType.PIGLIN, EntityType.ZOMBIE)) {
            look(180.0F, 2.0F);
            onServer(s -> {
                this.healthBefore = player(s).getHealth();
                this.attacker = summon(player(s), type, 6.0);
                Execution.request(player(s), this.attacker.getId());
            });
            pause(34);
            onServer(s -> {
                check("казнь в финальной форме: " + type.getUntranslatedName(), !this.attacker.isAlive());
                check("казнь не ранит игрока: " + type.getUntranslatedName(), player(s).getHealth() >= this.healthBefore);
            });
        }
        // Крипера можно подбросить: приём задан явно, чтобы проверить именно его.
        look(180.0F, 2.0F);
        onServer(s -> {
            this.healthBefore = player(s).getHealth();
            this.attacker = summon(player(s), EntityType.CREEPER, 6.0);
            Execution.request(player(s), this.attacker.getId(), Execution.Variant.LAUNCH);
        });
        pause(34);
        onServer(s -> {
            check("подброшенный крипер рвётся в воздухе", !this.attacker.isAlive());
            check("взрыв крипера никого не задел", player(s).getHealth() >= this.healthBefore);
        });
        pause(10);
        writeReaction();
        onServer(s -> HollowForm.toggle(player(s)));
        view(Perspective.FIRST_PERSON);
        pause(30);

        // ---- Превращение на уровне V целиком: злость, молния, колено, подъём, меч на плечо, выпад.
        look(180.0F, 2.0F);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            ModAttachments.data(player).hollowUsed = 0;
            HollowForm.toggle(player);
        });
        pause(16);
        shot("rite_rage");
        pause(16);
        shot("rite_scream");
        pause(8);
        shot("rite_strike");
        pause(18);
        shot("rite_kneel");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            this.healthBefore = player.getHealth();
            player.damage(player.getServerWorld().getDamageSources().generic(), 4.0F);
            check("в обряде урон не проходит", player.getHealth() >= this.healthBefore);
            check("в обряде время формы не тратится", ModAttachments.data(player).hollowUsed == 0);
            check("в обряде игрок не может ходить", Cinematic.isLocked(player));
        });
        pause(40);
        shot("rite_rise");
        pause(20);
        shot("rite_stand");
        pause(14);
        shot("rite_shoulder");
        pause(23);
        shot("rite_lunge");
        pause(30);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            check("обряд кончился, игрок снова свободен", !HollowForm.isTransforming(player) && !Cinematic.isLocked(player));
            check("после обряда форма финальная и включена", ModAttachments.data(player).hollowActive);
            check("после обряда время формы снова идёт", ModAttachments.data(player).hollowUsed > 0);
        });
        act(c -> check("камера вернулась после обряда", c.options.getPerspective() == Perspective.FIRST_PERSON));
        onServer(s -> HollowForm.toggle(player(s)));
        pause(10);

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
        writeBreak();
        this.writeEnd();
    }

    private void writeEnd() {
        pause(5);
        act(c -> {
            ZangetsuMod.LOGGER.info("Съёмка: готово, {} кадров", this.shotIndex);
            ZangetsuMod.LOGGER.info("ПРОВЕРКИ: пройдено {} из {}{}", this.checksPassed, this.checksTotal,
                    this.failed.isEmpty() ? "" : ", провалены: " + String.join("; ", this.failed));
            c.scheduleStop();
        });
    }

    /** Смертельный удар: Яхве ломает меч, игрок жив. Следующий сон — поиск настоящего среди фальшивых, настоящий подмигивает, потом меч снова с игроком. */
    /** Шкала формы заполняется за убитых монстров, и маску снова можно надеть. Небо при красной луне. */
    private void writeGauge() {
        look(180.0F, 2.0F);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            s.getCommandManager().executeWithPrefix(player.getCommandSource(), "zangetsu level 4");
            ZangetsuData data = ModAttachments.data(player);
            data.hollowUsed = HollowForm.BUDGET;
            HollowForm.toggle(player);
            check("шкала пуста: маску не надеть", !data.hollowActive);
            this.attacker = summon(player, EntityType.HUSK, 3.0);
            this.attacker.damage(player.getDamageSources().playerAttack(player), 1000.0F);
        });
        pause(4);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            ZangetsuData data = ModAttachments.data(player);
            check("убит монстр: шкала заполнилась на 5 секунд", data.hollowUsed == HollowForm.BUDGET - 100);
            HollowForm.skipRite = true;
            HollowForm.toggle(player);
            HollowForm.skipRite = false;
            check("после убийства маску снова можно надеть", data.hollowActive);
            this.healthBefore = data.hollowUsed;
            this.attacker = summon(player, EntityType.HUSK, 3.0);
            this.attacker.damage(player.getDamageSources().playerAttack(player), 1000.0F);
        });
        pause(2);
        onServer(s -> {
            ZangetsuData data = ModAttachments.data(player(s));
            check("в самой форме заполняется слабее", data.hollowUsed > this.healthBefore - 100 && data.hollowUsed < this.healthBefore);
            data.hollowLock = player(s).getServer().getOverworld().getTimeOfDay() + 24000L;
            int used = data.hollowUsed;
            this.attacker = summon(player(s), EntityType.HUSK, 3.0);
            this.attacker.damage(player(s).getDamageSources().playerAttack(player(s)), 1000.0F);
            check("разбитую маску убийства не чинят", data.hollowUsed == used && HollowForm.ticksLeft(player(s), data) == 0);
            data.hollowLock = 0L;
            data.hollowUsed = 0;
        });
        command("time set 6000");
        view(Perspective.FIRST_PERSON);
        pause(70);
        look(0.0F, -89.0F);
        pause(4);
        shot("sky_no_sun");
        look(-150.0F, -50.0F);
        pause(4);
        shot("sky_red_moon");
        command("time set 18000");
        look(0.0F, -89.0F);
        pause(6);
        shot("sky_no_moon");
        look(-150.0F, -50.0F);
        pause(4);
        shot("sky_red_moon_night");
        onServer(s -> HollowForm.end(player(s), false));
        command("time set 6000");
        pause(90);
        look(0.0F, -89.0F);
        pause(4);
        shot("sky_sun_back");
    }

    private boolean expectArena;

    /** Смерть в финальной форме от начала до разбитой маски. */
    private void writeRampage() {
        onServer(s -> {
            s.getCommandManager().executeWithPrefix(player(s).getCommandSource(), "zangetsu give");
            s.getCommandManager().executeWithPrefix(player(s).getCommandSource(), "zangetsu level 5");
            HollowForm.skipRite = true;
            HollowForm.toggle(player(s));
            HollowForm.skipRite = false;
        });
        view(Perspective.FIRST_PERSON);
        pause(30);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            check("форма включена", ModAttachments.data(player).hollowActive && HollowForm.isFinal(ModAttachments.data(player)));
            player.setHealth(3.0F);
            player.damage(player.getDamageSources().generic(), 1000.0F);
        });
        pause(2);
        onServer(s -> check("смерть в форме: сцена началась, игрок жив", player(s).isAlive() && com.zangetsu.logic.HollowRampage.isActive(player(s).getUuid())));
        if (this.expectArena) {
            onServer(s -> check("в тесноте: перенесён на отдельное поле", player(s).getWorld().getRegistryKey() == com.zangetsu.logic.HollowRampage.WORLD));
        }
        int[] frames = {10, 20, 40, 70, 96, 106, 125, 146, 162, 184, 204, 224, 244, 266, 300, 340, 380, 420, 440, 480, 524, 540, 560, 568, 584, 600, 610, 618, 636, 655, 668, 684, 696, 708, 713, 717, 720, 726, 738, 752, 766, 780, 806};
        int at = 2;
        for (int frame : frames) {
            pause(Math.max(1, frame - at));
            at = frame;
            shot("rampage_" + frame);
        }
        pause(com.zangetsu.logic.HollowRampage.END + 30 - at);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            ZangetsuData data = ModAttachments.data(player);
            check("сцена кончилась, игрок жив и свободен", player.isAlive() && !com.zangetsu.logic.HollowRampage.isActive(player.getUuid()) && !Cinematic.isLocked(player));
            if (this.expectArena) {
                check("в тесноте: вернулся туда, где умер", player.getWorld().getRegistryKey() == net.minecraft.world.World.OVERWORLD && player.getPos().squaredDistanceTo(0.5, -60.0, 0.5) < 4.0);
            }
            check("маска разбита, форма снята", !data.hollowActive);
            check("форма недоступна сутки", HollowForm.locked(player, data) && HollowForm.ticksLeft(player, data) == 0);
            HollowForm.toggle(player);
            check("снова надеть маску нельзя", !ModAttachments.data(player).hollowActive);
            check("от толпы ничего не осталось", player.getServerWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(40.0), m -> m.isAlive()).isEmpty());
        });
    }

    /** Все новые разрубы: обычной казнью и со спины, с паузой в замахе. */
    private void writeCuts() {
        // Эти команды работают от имени игрока, а не сервера.
        onServer(s -> {
            s.getCommandManager().executeWithPrefix(player(s).getCommandSource(), "zangetsu give");
            s.getCommandManager().executeWithPrefix(player(s).getCommandSource(), "zangetsu level 2");
        });
        pause(10);
        for (Execution.Variant variant : List.of(Execution.Variant.GYAKU, Execution.Variant.CHEST, Execution.Variant.SPLIT_UP, Execution.Variant.SLICES)) {
            String name = variant.name().toLowerCase(Locale.ROOT);
            look(180.0F, 2.0F);
            onServer(s -> {
                this.attacker = summon(player(s), EntityType.HUSK, 3.2);
                this.attacker.setHealth(1.0F);
                check("есть меч в руке", com.zangetsu.logic.SwordStacks.isHolding(player(s)));
                Execution.request(player(s), this.attacker.getId(), variant);
            });
            pause(14);
            shot("cut_" + name + "_1");
            pause(5);
            shot("cut_" + name + "_2");
            pause(9);
            shot("cut_" + name + "_3");
            pause(26);
            onServer(s -> check("разруб убивает: " + name, !this.attacker.isAlive()));
            pause(10);
        }
        for (Execution.Variant variant : List.of(Execution.Variant.QUARTER, Execution.Variant.TRIPLE, Execution.Variant.IAI, Execution.Variant.SLICES)) {
            String name = variant.name().toLowerCase(Locale.ROOT);
            look(180.0F, 2.0F);
            onServer(s -> {
                this.attacker = summon(player(s), EntityType.HUSK, 3.2);
                this.attacker.setHealth(1.0F);
                Execution.nextBehind = variant;
                Execution.behind(player(s), (net.minecraft.entity.LivingEntity) this.attacker);
            });
            pause(8);
            shot("behind_" + name + "_1");
            pause(7);
            shot("behind_" + name + "_2");
            pause(5);
            shot("behind_" + name + "_3");
            pause(6);
            shot("behind_" + name + "_4");
            pause(7);
            shot("behind_" + name + "_5");
            pause(40);
            onServer(s -> {
                check("со спины убивает: " + name, !this.attacker.isAlive());
                check("сцена кончилась: " + name, !Cinematic.isLocked(player(s)) && !Execution.inBehindScene(player(s)));
            });
            pause(10);
        }
    }

    private void writeBreak() {
        command("gamemode survival @p", "time set 6000", "tp @p 0.5 -60 0.5 180 0", "setblock 3 -60 0 minecraft:red_bed[facing=east,part=head]", "setblock 2 -60 0 minecraft:red_bed[facing=east,part=foot]");
        view(Perspective.FIRST_PERSON);
        look(180.0F, 0.0F);
        pause(12);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            player.setHealth(3.0F);
            player.damage(player.getDamageSources().generic(), 1000.0F);
        });
        pause(2);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            ZangetsuData data = ModAttachments.data(player);
            check("смертельный удар: игрок жив, сцена началась", player.isAlive() && SwordBreak.isActive(player.getUuid()));
            check("смертельный удар: меч забран и сломан", SwordStacks.find(player).isEmpty() && data.lost && data.broken && data.bonded);
        });
        pause(14);
        shot("break_void");
        pause(14);
        shot("break_sword_rises");
        pause(28);
        shot("break_figure_opens");
        pause(18);
        shot("break_arm_pocket");
        pause(13);
        shot("break_arm_out");
        pause(13);
        shot("break_arm_forward");
        pause(13);
        shot("break_arm_ready");
        pause(13);
        shot("break_sword_spin_slow");
        pause(9);
        shot("break_sword_spins");
        pause(8);
        shot("break_sword_launch");
        pause(6);
        shot("break_sword_flies");
        pause(4);
        shot("break_catch");
        pause(3);
        shot("break_recoil");
        pause(12);
        shot("break_grip");
        pause(22);
        shot("break_cracks");
        pause(26);
        shot("break_face_speaks");
        pause(38);
        shot("break_crush");
        pause(11);
        shot("break_snap");
        pause(4);
        shot("break_shards");
        pause(18);
        shot("break_shards_float");
        pause(21);
        shot("break_dissolve_1");
        pause(6);
        shot("break_figure_leaves");
        pause(6);
        shot("break_dissolve_3");
        pause(6);
        shot("break_dissolve_4");
        pause(6);
        shot("break_dissolve_5");
        pause(7);
        shot("break_dissolve_6");
        pause(7);
        shot("break_dissolve_7");
        pause(7);
        shot("break_dissolve_8");
        pause(7);
        shot("break_dissolve_9");
        onServer(s -> check("сцена: меч так и не вернулся", SwordStacks.find(player(s)).isEmpty()));
        pause(66);
        shot("break_after");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            check("после сцены: игрок свободен и жив", player.isAlive() && !SwordBreak.isActive(player.getUuid()) && !Cinematic.isLocked(player) && !player.hasNoGravity());
            check("после сцены: как после тотема", player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION) && player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.ABSORPTION));
        });
        // Сон: поле мечей и Яхве.
        command("time set 18000", "tp @p 0.5 -60 0.5 180 0");
        pause(6);
        sleep();
        waitFor("снится поле мечей", c -> c.world != null && c.world.getRegistryKey() == Dream.WORLD);
        pause(70);
        shot("trial_figure_comes");
        pause(50);
        onServer(s -> check("поле мечей: идёт испытание, мечи фальшивые", Trial.isActive(player(s).getUuid()) && fakes(player(s)) >= 40));
        shot("trial_speech");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            check("поле мечей: среди них ровно один настоящий", realSword(player) != null);
            check("поле мечей: кроме настоящего глаз снаружи", realSword(player) != null && fakes(player) > 0 && !Trial.isSearching(player.getUuid()));
            Trial.hurry(player);
        });
        pause(4);
        // Настоящий и фальшивый вблизи: встаём лицом к глазу, у настоящего лезвие справа, у фальшивого слева.
        view(Perspective.FIRST_PERSON);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            check("поиски начались", Trial.isSearching(player.getUuid()));
            faceEye(player, realSword(player));
        });
        pause(5);
        act(c -> {
            ThrownZangetsuEntity sword = nearestClient(c);
            if (sword != null) {
                lookAt(c, sword.getPos().add(0.0, 0.9, 0.0));
            }
        });
        pause(5);
        shot("trial_close_real");
        onServer(s -> faceEye(player(s), nearestFake(player(s))));
        pause(5);
        act(c -> {
            ThrownZangetsuEntity sword = nearestClient(c);
            if (sword != null) {
                lookAt(c, sword.getPos().add(0.0, 0.9, 0.0));
            }
        });
        pause(5);
        shot("trial_close_fake");
        // Взял подделку: она рассыпается.
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            ThrownZangetsuEntity fake = nearestFake(player);
            int before = fakes(player);
            check("подделка найдена", fake != null);
            if (fake != null) {
                Trial.pull(player, fake);
                check("взял подделку: она рассыпалась, игрок всё ещё во сне", Trial.fakesTaken(player.getUuid()) == 1 && !fake.isAlive() && fakes(player) == before - 1 && Dream.inDream(player));
            }
        });
        pause(10);
        shot("trial_fake_gone");
        onServer(s -> Trial.hurry(player(s)));
        pause(4);
        onServer(s -> check("минута прошла: настоящий подмигивает", Trial.isWinking(player(s).getUuid()) && realSword(player(s)) != null && realSword(player(s)).isWinking()));
        act(c -> {
            ThrownZangetsuEntity sword = nearestWinking(c);
            if (sword != null) {
                lookAt(c, sword.getPos().add(0.0, 0.8, 0.0));
            }
        });
        pause(14);
        shot("trial_wink_open");
        pause(16);
        shot("trial_wink_shut");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            ThrownZangetsuEntity real = realSword(player);
            if (real != null) {
                Trial.pull(player, real);
            }
        });
        pause(30);
        shot("trial_found");
        waitFor("проснулся с мечом", c -> c.world != null && c.world.getRegistryKey() == World.OVERWORLD);
        pause(20);
        shot("trial_back");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            ZangetsuData data = ModAttachments.data(player);
            check("нашёл настоящего: проснулся с мечом, меч больше не сломан", !Trial.isActive(player.getUuid()) && !data.broken && !data.lost && data.dream == null && SwordStacks.isHolding(player));
        });
    }

    /** Поставить игрока лицом к той стороне меча, где у него глаз: у настоящего это +z меча, у фальшивого −z. */
    private static void faceEye(ServerPlayerEntity player, ThrownZangetsuEntity sword) {
        if (sword != null) {
            Vec3d normal = Vec3d.fromPolar(0.0F, sword.getYaw()).multiply(sword.getKind() == ThrownZangetsuEntity.FAKE ? -1.0 : 1.0);
            Vec3d p = sword.getPos().add(normal.multiply(2.0));
            player.networkHandler.requestTeleport(p.x, sword.getY() + 0.6, p.z, 0.0F, 10.0F);
        }
    }

    private static int fakes(ServerPlayerEntity player) {
        return player.getServerWorld().getEntitiesByClass(ThrownZangetsuEntity.class, new Box(player.getBlockPos()).expand(70.0), s -> s.getKind() == ThrownZangetsuEntity.FAKE).size();
    }

    private static ThrownZangetsuEntity realSword(ServerPlayerEntity player) {
        return player.getServerWorld().getEntitiesByClass(ThrownZangetsuEntity.class, new Box(player.getBlockPos()).expand(90.0), s -> s.getKind() == ThrownZangetsuEntity.REAL).stream().findFirst().orElse(null);
    }

    private static ThrownZangetsuEntity nearestFake(ServerPlayerEntity player) {
        return player.getServerWorld().getEntitiesByClass(ThrownZangetsuEntity.class, new Box(player.getBlockPos()).expand(60.0), s -> s.getKind() == ThrownZangetsuEntity.FAKE).stream()
                .min(java.util.Comparator.comparingDouble(s -> s.squaredDistanceTo(player))).orElse(null);
    }

    private static ThrownZangetsuEntity nearestWinking(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return null;
        }
        return client.world.getEntitiesByClass(ThrownZangetsuEntity.class, client.player.getBoundingBox().expand(64.0), ThrownZangetsuEntity::isWinking).stream().findFirst().orElse(null);
    }

    private static final Vec3d MODEL_AT = new Vec3d(0.5, -50.0, 0.5);
    private ZangetsuSpiritEntity modelFigure;

    /** Модель одна в тёмной пустоте, с теми же камерами, что рендеры Blender. Масштаб 1: блок равен метру модели. */
    private void writeModel() {
        command("gamemode spectator @p", "time set 18000", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "tp @p 0.5 -45 12 180 0");
        act(c -> {
            c.options.hudHidden = true;
            c.options.getFov().setValue(30);
        });
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            this.modelFigure = ZangetsuSpiritEntity.summon(player.getServerWorld(), MODEL_AT, 0.0F, 1.0F, player.getUuid());
            this.modelFigure.keepWhile(() -> true);
            this.modelFigure.setPhase(ZangetsuSpiritEntity.WATCH);
            com.zangetsu.network.Net.send(player, new com.zangetsu.network.CallingPayload(com.zangetsu.network.CallingPayload.VOID_ON, "", 10, 0.0, 0.0, 0.0));
        });
        pause(190);
        modelView("model_front", 0.0, 15.0, 1.1, 0.2, 0.0, 0.9);
        modelView("model_quarter", -9.5, 11.0, 2.4, 0.2, 0.0, 0.9);
        modelView("model_side", -15.0, 0.0, 1.6, 0.0, -0.3, 0.9);
        modelView("model_back", 1.6, -15.0, 2.6, 0.0, 0.0, 0.9);
        modelView("model_as_068", -1.4, 6.8, 0.9, 0.72, 0.0, 1.32);
        modelView("model_as_037", 0.34, 2.6, 1.52, 0.06, 0.0, 1.68);
        modelView("model_face_038", 0.02, 1.25, 1.68, 0.01, 0.0, 1.86);
        modelView("model_mouth", 0.0, 0.62, 1.76, 0.0, 0.0, 1.79);
        modelView("model_face_quarter", -0.75, 0.95, 1.72, 0.0, 0.03, 1.84);
        onServer(s -> this.modelFigure.setArms(1));
        pause(92);
        modelView("model_as_010", 0.0, 3.0, 0.1, 0.0, 0.0, 1.56);
        modelView("model_arms", 1.0, 5.6, 0.45, 0.1, 0.0, 1.46);
        modelView("model_arms_near", -0.2, 1.6, 1.0, -0.75, 0.6, 1.5);
        pause(60);
        act(c -> ZangetsuMod.LOGGER.info("Съёмка: кадров в секунду с фигурой во весь экран: {}", c.getCurrentFps()));
        // Глаза живут: два кадра подряд с разницей в секунду и две.
        modelView("model_eyes_a", 0.02, 1.25, 1.68, 0.01, 0.0, 1.86);
        pause(30);
        modelView("model_eyes_b", 0.02, 1.25, 1.68, 0.01, 0.0, 1.86);
    }

    /** Камера как в Blender: глаз и цель заданы как x, вперёд от лица, вверх. */
    private void modelView(String name, double ex, double ef, double ez, double tx, double tf, double tz) {
        Vec3d eye = MODEL_AT.add(ex, ez - 1.62, ef);
        Vec3d target = MODEL_AT.add(tx, tz, tf);
        onServer(s -> player(s).networkHandler.requestTeleport(eye.x, eye.y, eye.z, 0.0F, 0.0F));
        pause(3);
        act(c -> lookAt(c, target));
        pause(3);
        shot(name);
    }

    private void sleep() {
        onServer(s -> player(s).trySleep(new BlockPos(2, -60, 0)));
    }

    /** Первый сон целиком: кровать, фигура, речь, меч; отказ и что он навсегда; затем сон снова и меч (сцену подбора проверяет основной сценарий). */
    private void writeFirstDream() {
        command("time set 18000", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "tp @p 0.5 -60 0.5 180 0",
                "setblock 3 -60 0 minecraft:red_bed[facing=east,part=head]", "setblock 2 -60 0 minecraft:red_bed[facing=east,part=foot]");
        look(180.0F, 0.0F);
        pause(20);
        shot("start");
        sleep();
        pause(10);
        onServer(s -> check("первый сон: игрок лёг и ещё не во сне", player(s).isSleeping() && !Calling.isActive(player(s).getUuid())));
        waitFor("снится сон", c -> c.world != null && c.world.getRegistryKey() == Calling.WORLD);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            check("первый сон: игрок там, в режиме приключения", Calling.inCalling(player) && player.interactionManager.getGameMode() == GameMode.ADVENTURE);
            BlockPos bed = Calling.bed(player);
            check("первый сон: его кровать у него за спиной", bed != null && player.getWorld().getBlockState(bed).isOf(Blocks.RED_BED) && bed.getZ() < player.getZ());
        });
        pause(50);
        shot("calling_emerge_1");
        pause(30);
        shot("calling_emerge_2");
        pause(40);
        shot("calling_eyes");
        pause(30);
        shot("calling_speech_1");
        pause(30);
        shot("calling_eyes_later");
        pause(60);
        shot("calling_speech_2");
        pause(40);
        onServer(s -> check("первый сон: меча ещё нет", Calling.swordOf(player(s)) == null));
        pause(26);
        shot("calling_arms_pocket");
        pause(16);
        shot("calling_arms_out");
        pause(14);
        shot("calling_arms_forward");
        pause(14);
        shot("calling_worthy");
        pause(40);
        shot("calling_sword_comes_out");
        waitFor("меч летит", c -> nearestClient(c) != null);
        pause(8);
        shot("calling_sword_flies");
        waitFor("меч вонзился", c -> stuckSword(c) != null);
        pause(8);
        shot("calling_sword_stuck");
        onServer(s -> check("первый сон: меч воткнут, выбор за игроком", Calling.isOffering(player(s)) && Calling.swordOf(player(s)) != null && !ModAttachments.data(player(s)).bonded));
        pause(100);
        shot("calling_choice");
        look(180.0F, 12.0F);
        pause(4);
        shot("calling_bed");
        look(0.0F, -28.0F);
        pause(4);
        shot("calling_figure_again");
        pause(40);
        shot("calling_figure_later");

        // Отказ.
        onServer(s -> Calling.refuse(player(s)));
        pause(30);
        onServer(s -> check("отказ: записан сразу", ModAttachments.data(player(s)).refused));
        shot("calling_wrath");
        pause(50);
        shot("calling_refuse_1");
        pause(70);
        onServer(s -> check("отказ: меч рассыпался", Calling.swordOf(player(s)) == null));
        shot("calling_refuse_dust");
        pause(120);
        shot("calling_refuse_3");
        pause(40);
        shot("calling_farewell");
        pause(50);
        shot("calling_eyes_closing");
        waitFor("глаза закрылись", c -> c.world != null && c.world.getEntitiesByClass(com.zangetsu.entity.ZangetsuSpiritEntity.class, c.player.getBoundingBox().expand(64.0), e -> e.getPhase() == com.zangetsu.entity.ZangetsuSpiritEntity.DISSOLVING).size() > 0);
        pause(16);
        shot("calling_dissolving");
        waitFor("проснулся без меча", c -> c.world != null && c.world.getRegistryKey() == World.OVERWORLD);
        pause(16);
        shot("calling_refused_morning");
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            ZangetsuData data = ModAttachments.data(player);
            check("отказ: проснулся без меча", !Calling.isActive(player.getUuid()) && !data.bonded && data.refused && data.dream == null && SwordStacks.find(player).isEmpty());
            check("отказ: режим игры прежний, уже утро", player.interactionManager.getGameMode() != GameMode.ADVENTURE && player.getWorld().isDay());
            ServerWorld dream = s.getWorld(Calling.WORLD);
            int beds = 0;
            for (int x = -2; x <= 2; x++) {
                for (int z = -8; z <= 8; z++) {
                    for (int y = 30; y < 70; y++) {
                        if (dream.getBlockState(new BlockPos(Math.floorMod(player.getUuid().hashCode(), 64) * 4000 + x, y, z)).isIn(net.minecraft.registry.tag.BlockTags.BEDS)) {
                            beds++;
                        }
                    }
                }
            }
            check("отказ: кровати во сне больше нет", beds == 0);
        });

        // Отказ навсегда: снова лёг, но сон не приходит.
        command("time set 18000");
        pause(6);
        sleep();
        pause(70);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            check("отказ навсегда: сна больше нет", !Calling.isActive(player.getUuid()) && !Calling.inCalling(player));
            player.wakeUp(true, true);
        });
        // Подбросить меч нельзя.
        onServer(s -> player(s).giveItemStack(new net.minecraft.item.ItemStack(com.zangetsu.registry.ModItems.ZANGETSU)));
        pause(4);
        onServer(s -> check("отказ навсегда: чужой меч не остаётся", SwordStacks.find(player(s)).isEmpty() && !ModAttachments.data(player(s)).bonded));

        // Команда сброса снимает отказ, и сон приходит снова: теперь меч берут.
        command("execute as @p run zangetsu reset", "time set 18000", "tp @p 0.5 -60 0.5 180 0");
        pause(6);
        onServer(s -> check("сброс снимает отказ", !ModAttachments.data(player(s)).refused));
        sleep();
        waitFor("снова снится сон", c -> c.world != null && c.world.getRegistryKey() == Calling.WORLD);
        pause(320);
    }

    /**
     * Подбор меча без скачков: вся сцена считается с шагом в четверть тика и сравниваются соседние позы. Сравниваются сами повороты, а не углы Эйлера:
     * у них бывают разрывы там, где рука в модели идёт совершенно плавно.
     */
    private void checkClaimSmooth(MinecraftClient c) {
        ClientState.Animation scene = ClientState.animation(c.player);
        if (scene == null) {
            check("сцена подбора идёт", false);
            return;
        }
        PlayerEntityRenderer renderer = (PlayerEntityRenderer) c.getEntityRenderDispatcher().getRenderer(c.player);
        PlayerEntityModel<AbstractClientPlayerEntity> model = renderer.getModel();
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        String[] names = {"голова", "корпус", "правая рука", "левая рука", "правая нога", "левая нога"};
        Quaternionf[] previous = new Quaternionf[parts.length];
        float[] lastBends = new float[4];
        Vector3f lastFist = null, lastBlade = null;
        float lastDip = 0.0F;
        float worstTurn = 0.0F, worstBend = 0.0F, worstFist = 0.0F, worstBlade = 0.0F, worstDip = 0.0F, worstTurnAt = 0.0F, worstBladeAt = 0.0F;
        String worstPart = "";
        int walk = scene.duration() - Awakening.CLAIM_TICKS;
        for (float tick = 0.25F; tick <= scene.duration(); tick += 0.25F) {
            float time = scene.startAge() + tick;
            model.setAngles(c.player, 0.0F, 0.0F, time, 0.0F, 0.0F);
            for (int i = 0; i < parts.length; i++) {
                Quaternionf now = new Quaternionf().rotationZYX(parts[i].roll, parts[i].yaw, parts[i].pitch);
                if (previous[i] != null) {
                    float turn = 2.0F * (float) Math.acos(Math.min(1.0F, Math.abs(now.dot(previous[i]))));
                    if (turn > worstTurn) {
                        worstTurn = turn;
                        worstTurnAt = tick - walk;
                        worstPart = names[i];
                    }
                }
                previous[i] = now;
            }
            float[] bends = PlayerPoses.bends(c.player);
            for (int i = 0; i < 4; i++) {
                float bend = bends == null ? 0.0F : bends[i];
                worstBend = Math.max(worstBend, Math.abs(bend - lastBends[i]));
                lastBends[i] = bend;
            }
            float[] root = PlayerPoses.root(c.player, time);
            float dip = root == null ? 0.0F : root[1];
            worstDip = Math.max(worstDip, Math.abs(dip - lastDip));
            lastDip = dip;
            PlayerPoses.SwordPose pose = tick - walk > Awakening.CLAIM_GRIP + 8.0F && tick < scene.duration() - 1.0F ? PlayerPoses.sword(c.player) : null;
            if (pose != null) {
                if (lastFist != null) {
                    worstFist = Math.max(worstFist, pose.fist().distance(lastFist));
                    float swing = pose.blade().angle(lastBlade);
                    if (swing > worstBlade) {
                        worstBlade = swing;
                        worstBladeAt = tick - walk;
                    }
                }
                lastFist = new Vector3f(pose.fist());
                lastBlade = new Vector3f(pose.blade());
            }
        }
        ZangetsuMod.LOGGER.info(String.format(Locale.ROOT, "Плавность подбора за 1/4 тика: поворот %.3f рад (%s, тик %.1f), сгиб %.3f, присед %.3f, кулак %.2f пикс, клинок %.3f рад (тик %.1f)",
                worstTurn, worstPart, worstTurnAt, worstBend, worstDip, worstFist, worstBlade, worstBladeAt));
        check("подбор меча: суставы без скачков", worstTurn < 0.09F && worstBend < 0.12F && worstDip < 0.2F);
        check("подбор меча: кулак и клинок идут плавно", worstFist < 0.9F && worstBlade < 0.09F);
    }

    // ---------------------------------------------------------------- проверка реакции

    /** Круг вместо кнопки казни: отличная зона, хорошая, промах, время вышло и удар, который должен был убить. */
    private void writeReaction() {
        onServer(s -> Reaction.enabled = true);
        onServer(s -> {
            float from = 0.5F, great = 0.05F, good = 0.17F;
            check("белая зона засчитывается чуть шире нарисованной", Reaction.judge(from - 0.012F, from, great, good) == Reaction.GREAT && Reaction.judge(from + great + 0.012F, from, great, good) == Reaction.GREAT);
            check("за белой идёт хорошая", Reaction.judge(from + great + 0.06F, from, great, good) == Reaction.GOOD);
            check("мимо зоны всё ещё промах", Reaction.judge(from - 0.1F, from, great, good) == Reaction.MISS && Reaction.judge(from + great + good + 0.05F, from, great, good) == Reaction.MISS);
            check("хорошая зона без белой тоже с запасом", Reaction.judge(from - 0.01F, from, 0.0F, good) == Reaction.GOOD && Reaction.judge(from + good + 0.01F, from, 0.0F, good) == Reaction.GOOD);
        });
        look(180.0F, 2.0F);
        // Враг, с которым игрок дерётся и которому остался один удар: круг появляется сам.
        onServer(s -> {
            this.attacker = summonHostile(player(s), EntityType.ZOMBIE, 3.2);
            this.attacker.setHealth(2.0F);
            this.attacker.setAttacker(player(s));
            this.healthBefore = player(s).getHealth();
        });
        waitFor("круг реакции", c -> ReactionCheck.isActive());
        shot("qte_ring");
        onServer(s -> {
            check("враг замер на проверке", Reaction.isTarget(this.attacker) && ((MobEntity) this.attacker).isAiDisabled());
            check("кнопки казни больше нет: проверка идёт сама", Reaction.isActive(player(s)));
            this.bondBefore = ModAttachments.data(player(s)).bond;
        });
        waitFor("стрелка у отличной зоны", c -> ReactionCheck.needle() >= ReactionCheck.zoneStart(ReactionCheck.GREAT) - 0.03F);
        shot("qte_needle");
        act(c -> ReactionCheck.answerAt(ReactionCheck.zoneMiddle(ReactionCheck.GREAT)));
        pause(3);
        onServer(s -> {
            ServerPlayerEntity player = player(s);
            check("отличная: игрок в сцене за спиной", Execution.inBehindScene(player));
            Vec3d toEnemy = this.attacker.getPos().subtract(player.getPos()).multiply(1.0, 0.0, 1.0).normalize();
            check("отличная: игрок стоит за спиной врага", toEnemy.dotProduct(this.attacker.getRotationVector().multiply(1.0, 0.0, 1.0).normalize()) > 0.5);
        });
        shot("qte_zoom");
        pause(2);
        shot("qte_mask");
        pause(5);
        shot("qte_phrase");
        pause(6);
        shot("qte_turn");
        pause(4);
        shot("qte_strike");
        pause(40);
        onServer(s -> {
            check("отличная: враг казнён", !this.attacker.isAlive());
            check("отличная: дружба выросла", ModAttachments.data(player(s)).bond > this.bondBefore);
            check("сцена кончилась, игрок свободен", !Cinematic.isLocked(player(s)) && !Execution.inBehindScene(player(s)));
        });
        pause(40);
        // Хорошая зона: обычная быстрая казнь.
        look(180.0F, 2.0F);
        onServer(s -> {
            this.attacker = summonHostile(player(s), EntityType.SKELETON, 3.2);
            this.attacker.setHealth(2.0F);
            this.attacker.setAttacker(player(s));
        });
        waitFor("круг реакции", c -> ReactionCheck.isActive());
        waitFor("стрелка у хорошей зоны", c -> ReactionCheck.needle() >= ReactionCheck.zoneStart(ReactionCheck.GOOD) - 0.03F);
        act(c -> ReactionCheck.answerAt(ReactionCheck.zoneMiddle(ReactionCheck.GOOD)));
        pause(3);
        onServer(s -> check("хорошая: казнь без сцены за спиной", Execution.isBusy(player(s)) && !Execution.inBehindScene(player(s))));
        shot("qte_good_answer");
        pause(30);
        onServer(s -> check("хорошая: враг казнён", !this.attacker.isAlive()));
        pause(40);
        // Промах: враг приходит в себя, и второй проверки на него сразу нет.
        look(180.0F, 2.0F);
        onServer(s -> {
            this.attacker = summonHostile(player(s), EntityType.PIGLIN, 3.2);
            this.attacker.setHealth(2.0F);
            this.attacker.setAttacker(player(s));
        });
        waitFor("круг реакции", c -> ReactionCheck.isActive());
        pause(Reaction.LEAD + 5);
        act(c -> ReactionCheck.answerAt(0.3F));
        pause(1);
        shot("qte_miss_answer");
        pause(2);
        onServer(s -> {
            check("промах: враг не остался замершим", !((MobEntity) this.attacker).isAiDisabled());
            check("промах: игрок не в казни", !Execution.isBusy(player(s)));
        });
        pause(40);
        onServer(s -> {
            check("промах: второй проверки на него нет", !Reaction.isActive(player(s)));
            this.attacker.discard();
        });
        pause(5);
        // Время вышло без клика: то же, что промах.
        look(180.0F, 2.0F);
        onServer(s -> {
            this.attacker = summonHostile(player(s), EntityType.VINDICATOR, 3.2);
            this.attacker.setHealth(2.0F);
            this.attacker.setAttacker(player(s));
        });
        waitFor("круг реакции", c -> ReactionCheck.isActive());
        pause(Reaction.LEAD + 38);
        onServer(s -> {
            check("время вышло: проверка кончилась", !Reaction.isActive(player(s)));
            check("время вышло: враг не остался замершим", !((MobEntity) this.attacker).isAiDisabled());
            this.attacker.discard();
        });
        pause(40);
        // Удар, который убил бы: враг остаётся с каплей здоровья, и начинается проверка.
        look(180.0F, 2.0F);
        onServer(s -> {
            this.attacker = summonHostile(player(s), EntityType.ZOMBIE, 3.2);
            this.attacker.timeUntilRegen = 0;
            this.attacker.damage(player(s).getServerWorld().getDamageSources().playerAttack(player(s)), 1000.0F);
            check("смертельный удар: враг жив", this.attacker.isAlive() && this.attacker.getHealth() > 0.0F);
            check("смертельный удар: началась проверка", Reaction.isActive(player(s)));
        });
        pause(Reaction.LEAD + 34);
        onServer(s -> {
            this.attacker.timeUntilRegen = 0;
            this.attacker.damage(player(s).getServerWorld().getDamageSources().playerAttack(player(s)), 1000.0F);
            check("после промаха его можно просто добить", !this.attacker.isAlive());
        });
        pause(40);
        // Мирного не трогает и за низкое здоровье.
        onServer(s -> {
            this.attacker = summonHostile(player(s), EntityType.VILLAGER, 3.2);
            this.attacker.setHealth(1.0F);
            this.attacker.setAttacker(player(s));
        });
        pause(8);
        onServer(s -> {
            check("мирного проверка не трогает", !Reaction.isActive(player(s)));
            this.attacker.discard();
            Reaction.enabled = false;
        });
        pause(5);
    }

    // ---------------------------------------------------------------- покадровые замеры

    private final List<float[]> walkFrames = new ArrayList<>();
    private boolean walkDone;
    private final java.util.Map<String, float[][][]> clothLast = new java.util.HashMap<>();
    private final java.util.Map<String, int[][]> clothFlips = new java.util.HashMap<>();
    private final java.util.Map<String, int[][]> clothLastFlip = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> flipsByTick = new java.util.TreeMap<>();
    private int clothFrames;
    private float clothWorstStep;
    private boolean clothDone;

    /** Каждый отрисованный кадр: где игрок и камера в сцене подбора, как ходит ткань в обряде. */
    private void onFrame(MinecraftClient c) {
        if (c.player == null) {
            return;
        }
        ClientState.Animation scene = ClientState.animation(c.player);
        int type = scene == null ? 0 : scene.type();
        float td = c.getRenderTickCounter().getTickDelta(false);
        if (type == AnimationPayload.TOUCH_HANDLE) {
            var camera = c.gameRenderer.getCamera();
            Vec3d p = c.player.getLerpedPos(td), at = camera.getPos();
            this.walkFrames.add(new float[]{c.player.age + td - scene.startAge(), (float) p.x, (float) p.y, (float) p.z, c.player.getYaw(td), c.player.getBodyYaw(),
                    (float) at.x, (float) at.y, (float) at.z, camera.getYaw(), camera.getPitch(), c.player.limbAnimator.getSpeed(td), scene.duration(),
                    (float) (c.player.getX() - c.player.prevX), (float) (c.player.getZ() - c.player.prevZ), td});
        } else if (!this.walkFrames.isEmpty() && !this.walkDone) {
            this.walkDone = true;
            this.finishWalk();
        }
        if (type == AnimationPayload.HOLLOW_FINAL) {
            FormPhysics physics = FormPhysics.of(c.player);
            float tick = c.player.age + td - scene.startAge();
            this.clothFrames++;
            this.measureCloth("cloak", physics, tick);
            this.measureCloth("mantle", physics, tick);
        } else if (this.clothFrames > 0 && !this.clothDone) {
            this.clothDone = true;
            this.finishCloth();
        }
    }

    private void measureCloth(String key, FormPhysics physics, float tick) {
        FormPhysics.Cloth cloth = physics.existingCloth(key);
        if (cloth == null) {
            return;
        }
        Vector3f[][] grid = cloth.local(physics.lastBodyFromSim());
        float[][][] last = this.clothLast.get(key);
        int[][] flips = this.clothFlips.computeIfAbsent(key, k -> new int[grid.length][grid[0].length]);
        int[][] lastFlip = this.clothLastFlip.computeIfAbsent(key, k -> new int[grid.length][grid[0].length]);
        float[][][] now = new float[grid.length][grid[0].length][6];
        for (int c = 0; c < grid.length; c++) {
            for (int j = 0; j < grid[0].length; j++) {
                Vector3f p = grid[c][j];
                float dx = 0.0F, dy = 0.0F, dz = 0.0F;
                if (last != null) {
                    dx = p.x - last[c][j][0];
                    dy = p.y - last[c][j][1];
                    dz = p.z - last[c][j][2];
                    float[] before = last[c][j];
                    float size = dx * dx + dy * dy + dz * dz, sizeBefore = before[3] * before[3] + before[4] * before[4] + before[5] * before[5];
                    if (size < 100.0F) {
                        this.clothWorstStep = Math.max(this.clothWorstStep, MathHelper.sqrt(size));
                    }
                    // Дрожь: точка рывком пошла обратно, и это повторилось через пару кадров. Плавная раскачка так не делает.
                    if (size > 0.04F && sizeBefore > 0.04F && dx * before[3] + dy * before[4] + dz * before[5] < -0.5F * MathHelper.sqrt(size * sizeBefore)) {
                        if (this.clothFrames - lastFlip[c][j] <= 4) {
                            flips[c][j]++;
                            this.flipsByTick.merge(((int) tick) / 10 * 10, 1, Integer::sum);
                        }
                        lastFlip[c][j] = this.clothFrames;
                    }
                }
                now[c][j][0] = p.x;
                now[c][j][1] = p.y;
                now[c][j][2] = p.z;
                now[c][j][3] = dx;
                now[c][j][4] = dy;
                now[c][j][5] = dz;
            }
        }
        this.clothLast.put(key, now);
    }

    private void finishCloth() {
        int total = 0;
        StringBuilder worst = new StringBuilder();
        for (var entry : this.clothFlips.entrySet()) {
            int best = 0, bc = 0, bj = 0;
            int[][] flips = entry.getValue();
            for (int c = 0; c < flips.length; c++) {
                for (int j = 0; j < flips[0].length; j++) {
                    total += flips[c][j];
                    if (flips[c][j] > best) {
                        best = flips[c][j];
                        bc = c;
                        bj = j;
                    }
                }
            }
            worst.append(entry.getKey()).append(": до ").append(best).append(" (столбец ").append(bc).append(", строка ").append(bj).append(") ");
            // Где дрожит: сумма по строкам и столбцам.
            int[] byRow = new int[flips[0].length], byColumn = new int[flips.length];
            for (int c = 0; c < flips.length; c++) {
                for (int j = 0; j < flips[0].length; j++) {
                    byRow[j] += flips[c][j];
                    byColumn[c] += flips[c][j];
                }
            }
            worst.append("строки ").append(java.util.Arrays.toString(byRow)).append(" столбцы ").append(java.util.Arrays.toString(byColumn)).append("; ");
        }
        ZangetsuMod.LOGGER.info("Дрожь ткани в обряде: кадров {}, повторных рывков {}, самый большой шаг {} пикс; {}по времени {}", this.clothFrames, total,
                String.format(Locale.ROOT, "%.2f", this.clothWorstStep), worst, this.flipsByTick);
        this.lastClothJitter = total;
        check("плащ в обряде не дрожит", total < 250);
    }

    private int lastClothJitter = -1;

    private void finishWalk() {
        float worstMove = 0.0F, worstTurn = 0.0F, worstStep = 0.0F, moveAt = 0.0F, turnAt = 0.0F;
        float lastMove = -1.0F, lastTurn = 0.0F, lastStep = -1.0F;
        float[] before = null;
        for (float[] f : this.walkFrames) {
            if (before != null && f[0] - before[0] > 0.01F && f[0] - before[0] < 1.5F) {
                float dt = f[0] - before[0];
                float move = (float) Math.sqrt((f[6] - before[6]) * (f[6] - before[6]) + (f[7] - before[7]) * (f[7] - before[7]) + (f[8] - before[8]) * (f[8] - before[8])) / dt;
                float turn = MathHelper.wrapDegrees(f[9] - before[9]) / dt;
                float step = (float) Math.sqrt((f[1] - before[1]) * (f[1] - before[1]) + (f[3] - before[3]) * (f[3] - before[3])) / dt;
                if (lastMove >= 0.0F) {
                    if (Math.abs(move - lastMove) > worstMove) {
                        worstMove = Math.abs(move - lastMove);
                        moveAt = f[0];
                    }
                    if (Math.abs(turn - lastTurn) > worstTurn) {
                        worstTurn = Math.abs(turn - lastTurn);
                        turnAt = f[0];
                    }
                    worstStep = Math.max(worstStep, Math.abs(step - lastStep));
                }
                lastMove = move;
                lastTurn = turn;
                lastStep = step;
            }
            before = f;
        }
        ZangetsuMod.LOGGER.info(String.format(Locale.ROOT, "Шаг к мечу, скачок скорости за кадр: камера %.3f блока/тик (тик %.1f), поворот камеры %.2f°/тик (тик %.1f), игрок %.3f блока/тик",
                worstMove, moveAt, worstTurn, turnAt, worstStep));
        check("шаг к мечу: камера и игрок идут без рывков", worstMove < 0.12F && worstTurn < 2.5F && worstStep < 0.05F);
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
