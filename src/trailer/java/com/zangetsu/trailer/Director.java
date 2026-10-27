package com.zangetsu.trailer;

import com.zangetsu.ZangetsuMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.option.Perspective;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/**
 * Режиссёр съёмки. Создаёт мир, готовит сцену за сценой (в обычном времени), а затем «крутит плёнку»:
 * кадр за кадром, шаг в шаг с сервером, по 60 кадров на секунду ролика.
 *
 * <p>Настройки (системные свойства): {@code zangetsu.trailer.scenes} — какие сцены снимать (через запятую,
 * по умолчанию все), {@code zangetsu.trailer.video} — файл видео (нужен ffmpeg), {@code zangetsu.trailer.stills}
 * — папка стоп-кадров, {@code zangetsu.trailer.stillStep} — каждый какой кадр сохранять.
 */
public final class Director {
    public static final int FPS = 60;
    private static final long SEED = 20261027L;
    /** Первой сцене после загрузки мира — время на генерацию кусков вокруг площадки. */
    private static final int FIRST_SETTLE = 300;
    private static final int MAX_SETTLE = 900;

    private enum Phase { BOOT, LOADING, SETUP, SETTLE, PREROLL, ROLLING, DONE }

    private static List<Scene> scenes = List.of();
    private static Phase phase = Phase.BOOT;
    private static int sceneIndex = -1;
    private static Scene scene;
    private static int wait;
    private static boolean firstScene = true;
    private static volatile boolean setupDone;
    private static int frame;
    private static volatile double video;
    private static double gameTicks;
    private static float tickDelta;
    private static float frameTicks = 1.0F / 3.0F;
    private static int nextEvent;
    private static Scene.Cut activeCut;
    private static boolean frameOpen;
    private static int stillStep = 30;
    private static long started;
    private static final List<String> LOG = new ArrayList<>();
    /** Часы для взгляда глаза: в съёмке идут по кадрам (1/60 с), вне её — по настоящему времени. */
    private static long eyeNanos;
    private static long lastRealNanos = System.nanoTime();

    /** Что держит игрок (ставят события сцены, держится каждый кадр). */
    static boolean sneaking;
    static Vec3d walkTo;

    private Director() {
    }

    // ---------------------------------------------------------------- состояние для миксинов

    public static boolean running() {
        return phase != Phase.BOOT && phase != Phase.DONE;
    }

    public static boolean rolling() {
        return phase == Phase.ROLLING;
    }

    public static double video() {
        return video;
    }

    public static float tickDelta() {
        return tickDelta;
    }

    public static float frameTicks() {
        return frameTicks;
    }

    public static long eyeNanos() {
        long now = System.nanoTime();
        if (!rolling()) {
            eyeNanos += now - lastRealNanos;
        }
        lastRealNanos = now;
        return eyeNanos;
    }

    // ---------------------------------------------------------------- запуск

    static void init(List<Scene> all) {
        String wanted = System.getProperty("zangetsu.trailer.scenes", "all");
        if (wanted.equals("all")) {
            scenes = all;
        } else {
            Set<String> names = Arrays.stream(wanted.split(",")).map(String::trim).collect(Collectors.toSet());
            scenes = all.stream().filter(s -> names.contains(s.name)).toList();
        }
        stillStep = Integer.getInteger("zangetsu.trailer.stillStep", 30);
        ZangetsuMod.LOGGER.info("Трейлер: сцены {}", scenes.stream().map(s -> s.name).toList());
        TrailerCamera.setClock(() -> video);
    }

    /** Каждый тик клиента (вне съёмки кадров — подготовка). */
    static void onClientTick(MinecraftClient client) {
        switch (phase) {
            case BOOT -> {
                // Главное меню (или экран первого запуска) — ресурсы загружены, можно создавать мир.
                if (client.getOverlay() == null && client.world == null && client.currentScreen != null) {
                    phase = Phase.LOADING;
                    createWorld(client);
                }
            }
            case LOADING -> {
                if (client.world != null && client.player != null && client.getServer() != null) {
                    if (++wait > 40) {
                        options(client);
                        started = System.currentTimeMillis();
                        try {
                            startRecording(client);
                        } catch (IOException e) {
                            ZangetsuMod.LOGGER.error("Трейлер: не начать запись", e);
                        }
                        nextScene(client);
                    }
                }
            }
            case SETUP -> {
                hold(client);
                if (setupDone) {
                    scene.clientSetup.accept(client);
                    phase = Phase.SETTLE;
                    wait = 0;
                }
            }
            case SETTLE -> {
                hold(client);
                wait++;
                int need = scene.settle + (firstScene ? FIRST_SETTLE : 0);
                boolean ready = client.worldRenderer.isTerrainRenderComplete();
                if (wait >= need && (ready || wait > need + MAX_SETTLE)) {
                    firstScene = false;
                    phase = Phase.PREROLL;
                    wait = 0;
                    video = scene.start;
                    Insert.show(scene.insert);
                    activeCut = scene.cutAt(scene.start);
                    TrailerCamera.setShot(activeCut.shot(), activeCut.start(), scene.cutEnd(activeCut));
                }
            }
            case PREROLL -> {
                hold(client);
                if (++wait > 4) {
                    frame = scene.startFrame();
                    gameTicks = 0.0;
                    nextEvent = 0;
                    Lockstep.enable(true);
                    phase = Phase.ROLLING;
                    LOG.add(String.format(Locale.ROOT, "scene %s %.3f %.3f real %d", scene.name, scene.start, scene.end,
                            System.currentTimeMillis() - started));
                }
            }
            default -> {
            }
        }
    }

    private static void nextScene(MinecraftClient client) {
        sceneIndex++;
        if (sceneIndex >= scenes.size()) {
            finish(client);
            return;
        }
        scene = scenes.get(sceneIndex);
        ZangetsuMod.LOGGER.info("Трейлер: готовим сцену {} ({}–{} с)", scene.name, scene.start, scene.end);
        phase = Phase.SETUP;
        setupDone = false;
        sneaking = false;
        walkTo = null;
        Insert.show(null);
        TrailerCamera.setActive(true);
        TrailerCamera.setShot(Shots.sweep(scene.sweepCenter != null ? scene.sweepCenter : () -> Stage.center), 0.0, 1.0);
        Lockstep.onServer(() -> {
            scene.setup.accept(client.getServer());
            setupDone = true;
        });
    }

    // ---------------------------------------------------------------- кадр

    /** Начало кадра: события, тики сервера; вернуть число тиков клиента. */
    public static int beginFrame() {
        MinecraftClient client = MinecraftClient.getInstance();
        hold(client);
        video = frame / (double) FPS;
        eyeNanos += 1_000_000_000L / FPS;
        Scene.Cut cut = scene.cutAt(video);
        if (cut != activeCut) {
            activeCut = cut;
            TrailerCamera.setShot(cut.shot(), cut.start(), scene.cutEnd(cut));
        }
        while (nextEvent < scene.events.size() && scene.events.get(nextEvent).time() <= video + 1.0E-6) {
            try {
                scene.events.get(nextEvent).action().run();
            } catch (RuntimeException e) {
                ZangetsuMod.LOGGER.error("Трейлер: событие {} упало", scene.events.get(nextEvent).time(), e);
            }
            nextEvent++;
        }
        float speed = scene.speedAt(video);
        double before = gameTicks;
        gameTicks += speed / 3.0;
        int ticks = (int) (Math.floor(gameTicks + 1.0E-6) - Math.floor(before + 1.0E-6));
        for (int i = 0; i < ticks; i++) {
            Lockstep.tickServer();
        }
        if (ticks > 0) {
            // Пакеты с сервера идут через локальный канал — дать им дойти до очереди клиента.
            try {
                Thread.sleep(4);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        tickDelta = (float) Math.min(0.9999, Math.max(0.0, gameTicks + 1.0E-6 - Math.floor(gameTicks + 1.0E-6)));
        frameTicks = speed / 3.0F;
        frameOpen = true;
        return ticks;
    }

    /** Кадр нарисован: записать и перейти к следующему. */
    public static void endFrame() {
        if (!frameOpen) {
            return;
        }
        frameOpen = false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (Recorder.recordingVideo()) {
            Recorder.captureVideo(client.getFramebuffer());
        }
        if (Recorder.recordingStills()) {
            boolean cutStart = Math.abs(activeCut.start() * FPS - frame) < 0.5;
            if ((frame - scene.startFrame()) % stillStep == 0 || cutStart) {
                Recorder.captureStill(client.getFramebuffer(), String.format(Locale.ROOT, "%05d_%s_%s", frame, scene.name, activeCut.name()));
            }
        }
        if (frame % 300 == 0) {
            ZangetsuMod.LOGGER.info("Трейлер: кадр {} ({} с), прошло {} с", frame, String.format(Locale.ROOT, "%.2f", video),
                    (System.currentTimeMillis() - started) / 1000);
        }
        frame++;
        if (frame >= scene.endFrame()) {
            Lockstep.enable(false);
            Insert.show(null);
            nextScene(client);
        }
    }

    /** Что должно держаться каждый кадр: никакого интерфейса, вид «со стороны», нажатые клавиши. */
    private static void hold(MinecraftClient client) {
        if (client.currentScreen != null && client.world != null) {
            // Ни экрана смерти, ни меню в кадре: герой бессмертен, а меню закрываем.
            if (client.player != null && client.player.isDead()) {
                client.player.requestRespawn();
            }
            client.setScreen(null);
        }
        client.options.hudHidden = true;
        if (client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
        client.options.sneakKey.setPressed(sneaking);
        if (client.player != null && walkTo != null) {
            Vec3d d = walkTo.subtract(client.player.getPos());
            if (d.horizontalLength() < 1.7) {
                walkTo = null;
                client.options.forwardKey.setPressed(false);
            } else {
                float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F;
                client.player.setYaw(yaw);
                client.player.setHeadYaw(yaw);
                client.player.setPitch(8.0F);
                client.options.forwardKey.setPressed(true);
            }
        } else {
            client.options.forwardKey.setPressed(false);
        }
    }

    // ---------------------------------------------------------------- мир и настройки

    private static void createWorld(MinecraftClient client) {
        GameRules rules = new GameRules();
        rules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
        rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        rules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
        rules.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, null);
        rules.get(GameRules.SEND_COMMAND_FEEDBACK).set(false, null);
        rules.get(GameRules.LOG_ADMIN_COMMANDS).set(false, null);
        rules.get(GameRules.DO_MOB_LOOT).set(false, null);
        rules.get(GameRules.DO_FIRE_TICK).set(false, null);
        rules.get(GameRules.DO_MOB_GRIEFING).set(false, null);
        rules.get(GameRules.SHOW_DEATH_MESSAGES).set(false, null);
        String name = "trailer-" + System.currentTimeMillis();
        LevelInfo info = new LevelInfo(name, GameMode.SURVIVAL, false, Difficulty.NORMAL, true, rules, DataConfiguration.SAFE_MODE);
        client.createIntegratedServerLoader().createAndStart(name, info, new GeneratorOptions(SEED, false, false),
                registries -> registries.getWrapperOrThrow(RegistryKeys.WORLD_PRESET)
                        .getOrThrow(WorldPresets.DEFAULT).value().createDimensionsRegistryHolder(),
                client.currentScreen);
    }

    private static void options(MinecraftClient client) {
        GameOptions o = client.options;
        o.getGamma().setValue(0.7);
        o.getParticles().setValue(ParticlesMode.ALL);
        o.getViewDistance().setValue(10);
        o.getCloudRenderMode().setValue(CloudRenderMode.FANCY);
        o.getEntityShadows().setValue(true);
        o.getBobView().setValue(false);
        o.getMaxFps().setValue(260);
        o.getEnableVsync().setValue(false);
        o.getDamageTiltStrength().setValue(0.0);
        o.getDistortionEffectScale().setValue(0.0);
        o.getFovEffectScale().setValue(0.0);
        o.getEntityDistanceScaling().setValue(5.0);
        o.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
        o.pauseOnLostFocus = false;
        client.chunkCullingEnabled = false;
        client.getToastManager().clear();
    }

    private static void startRecording(MinecraftClient client) throws IOException {
        String videoFile = System.getProperty("zangetsu.trailer.video");
        if (videoFile != null && !videoFile.isBlank()) {
            Recorder.startVideo(Path.of(videoFile), client.getFramebuffer().textureWidth, client.getFramebuffer().textureHeight);
        }
        String stills = System.getProperty("zangetsu.trailer.stills");
        if (stills != null && !stills.isBlank()) {
            Recorder.startStills(Path.of(stills));
        }
    }

    private static void finish(MinecraftClient client) {
        phase = Phase.DONE;
        Lockstep.enable(false);
        TrailerCamera.setActive(false);
        Recorder.finishVideo();
        Recorder.finishStills();
        LOG.add("done real " + (System.currentTimeMillis() - started));
        try {
            Files.write(client.runDirectory.toPath().resolve("trailer-log.txt"), LOG);
        } catch (IOException e) {
            ZangetsuMod.LOGGER.error("Трейлер: журнал не записан", e);
        }
        ZangetsuMod.LOGGER.info("Трейлер: всё снято за {} с", (System.currentTimeMillis() - started) / 1000);
        client.scheduleStop();
    }
}
