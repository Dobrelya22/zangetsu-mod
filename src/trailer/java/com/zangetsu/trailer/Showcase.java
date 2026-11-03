package com.zangetsu.trailer;

import static com.zangetsu.trailer.Shots.arc;
import static com.zangetsu.trailer.Shots.eye;
import static com.zangetsu.trailer.Shots.hero;
import static com.zangetsu.trailer.Shots.p;
import static com.zangetsu.trailer.Shots.shot;

import com.zangetsu.logic.Eye;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.registry.ModAttachments;
import com.zangetsu.trailer.Shots.P;
import com.zangetsu.trailer.TrailerCamera.Shot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.joml.Quaternionf;

/**
 * Витрина для проверки графики (-PtrailerScript=showcase): крупные планы меча и маски со всех сторон и
 * анимации ударов — со стороны и глазами игрока. Снимается тем же движком, что и трейлер, только кадры
 * нужны не для ролика, а чтобы их рассмотреть.
 */
final class Showcase {
    private final List<Scene> scenes = new ArrayList<>();
    private Scene s;

    static List<Scene> build() {
        Showcase showcase = new Showcase();
        showcase.write();
        showcase.scenes.forEach(Scene::seal);
        return showcase.scenes;
    }

    private void write() {
        sword("sword", 0.0, Eye.OPEN);
        sword("burning", 5.0, Eye.BURNING);
        held();
        mask("mask", 12.0, 4);
        swings();
        auto();
        getsuga();
        firstPerson();
        mask("final", 36.0, 5);
        finalGetsuga();
    }

    // ---------------------------------------------------------------- меч крупно

    private void sword(String name, double t0, int eyeState) {
        scene(name, t0, t0 + 5.0);
        s.setup = Stage::arena;
        s.settle = 10;
        s.sweepCenter = () -> Stage.at(0, 52, 0);
        Scene scene = s;
        scene.clientSetup = c -> scene.insert = new Insert.Spec(Stage.at(0, 60, 0), new Quaternionf(), eyeState, v -> 1.0, false,
                Insert.light(15, 15));
        // Рукоять — в Stage.at(0, 60, 0), клинок вдоль +X (острие в 1,18 блока), глаз смотрит в +Z.
        P grip = p(0, 60, 0);
        cut(t0, "full", shot(grip.plus(0.62, 0.05, 2.0), grip.plus(0.55, 0, 0), 42));
        cut(t0 + 1.0, "hilt", shot(grip.plus(-0.02, 0.12, 0.5), grip.plus(-0.02, -0.02, 0), 44));
        cut(t0 + 2.0, "blade34", shot(grip.plus(1.15, -0.55, 0.85), grip.plus(0.7, 0, 0), 46));
        cut(t0 + 3.0, "pommel", shot(grip.plus(-0.3, -0.1, 0.55), grip.plus(-0.26, -0.22, 0), 46));
        cut(t0 + 4.0, "eye", shot(eye(0.06, 0.04, 0.42), eye(0, 0, 0), 34));
    }

    // ---------------------------------------------------------------- меч в руке

    private void held() {
        scene("held", 10.0, 12.0);
        s.setup = srv -> Script.base(srv, 4, false);
        cut(10.0, "held_side", shot(arc(2.6, 265, 1.1, 2.6, 285, 1.1), hero().plus(0, 1.0, 0), 44));
        cut(11.0, "held_back", shot(arc(2.4, 25, 1.4, 2.4, 345, 1.4), hero().plus(0, 1.0, 0), 44));
    }

    // ---------------------------------------------------------------- маска

    /** Маска со всех сторон и её трещины: уровень 4 — обычная, уровень 5 — финальная с рогом. */
    private void mask(String name, double t0, int level) {
        scene(name, t0, t0 + 5.0);
        s.settle = 70;
        s.setup = srv -> Script.base(srv, level, true);
        cut(t0, name + "_front", shot(arc(1.75, 180, 1.55), p(0, 1.55, 0), 40));
        cut(t0 + 1.0, name + "_34", shot(arc(1.85, 215, 1.65), p(0, 1.58, 0), 40));
        cut(t0 + 2.0, name + "_side", shot(arc(2.0, 270, 1.6), p(0, 1.62, 0), 40));
        cut(t0 + 3.0, name + "_top", shot(arc(1.4, 160, 2.3), p(0, 1.6, 0), 42));
        cut(t0 + 4.0, name + "_crack", shot(arc(1.75, 190, 1.55), p(0, 1.55, 0), 40));
        server(t0 + 4.0, srv -> ModAttachments.data(Stage.player(srv)).hollowUsed = HollowForm.BUDGET - 601);
        server(t0 + 4.33, srv -> ModAttachments.data(Stage.player(srv)).hollowUsed = HollowForm.BUDGET - 301);
        server(t0 + 4.66, srv -> ModAttachments.data(Stage.player(srv)).hollowUsed = HollowForm.BUDGET - 101);
    }

    /** Гецуга финальной формы: та же анимация, волна крупнее. */
    private void finalGetsuga() {
        scene("final_getsuga", 41.0, 43.0);
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 5, true);
        speed(41.0, 0.4F);
        server(41.2, srv -> Stage.getsuga(srv, 180.0F));
        cut(41.0, "wave", shot(arc(3.4, 25, 1.6), p(0, 1.3, -2.0), 52));
    }

    // ---------------------------------------------------------------- анимации со стороны

    /** Удары игрока: серия замахов в замедлении, камера сбоку и спереди. */
    private void swings() {
        scene("swing", 17.0, 21.0);
        s.setup = srv -> Script.base(srv, 4, false);
        speed(17.0, 0.35F);
        for (int i = 0; i < 6; i++) {
            client(17.1 + i * 0.62, () -> MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND));
        }
        cut(17.0, "swing_side", shot(arc(3.2, 270, 1.1), p(0, 1.2, 0), 46));
        cut(19.0, "swing_front", shot(arc(3.2, 205, 1.2), p(0, 1.2, 0), 46));
    }

    /** Меч бьёт сам: зомби подходит — удар. */
    private void auto() {
        scene("auto", 21.0, 24.0);
        s.setup = srv -> {
            ServerPlayerEntity p = Script.base(srv, 4, false);
            Stage.spawn(p, "A1", EntityType.ZOMBIE, Stage.at(0, 0, -5), 0.0F, false, 0.0F);
            Stage.spawn(p, "A2", EntityType.ZOMBIE, Stage.at(1.5, 0, -6), 0.0F, false, 0.0F);
        };
        server(21.0, srv -> Stage.wake(srv, "A"));
        speed(21.0, 0.5F);
        cut(21.0, "auto_side", shot(arc(4.0, 255, 1.2), p(0, 1.1, -1.5), 46));
        cut(22.5, "auto_front", shot(arc(3.4, 160, 1.3), p(0, 1.2, 0), 46));
    }

    /** Гецуга Теншо: замах — волна. */
    private void getsuga() {
        scene("getsuga", 24.0, 28.0);
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 4, true);
        speed(24.0, 0.4F);
        server(24.2, srv -> Stage.getsuga(srv, 180.0F));
        server(26.2, srv -> Stage.getsuga(srv, 180.0F));
        cut(24.0, "getsuga_side", shot(arc(3.6, 268, 1.2), p(0, 1.3, -0.8), 48));
        cut(26.0, "getsuga_back", shot(arc(3.4, 25, 1.6), p(0, 1.3, -2.0), 52));
    }

    // ---------------------------------------------------------------- глазами игрока

    private void firstPerson() {
        scene("fp_swing", 28.0, 31.0);
        s.firstPerson = true;
        s.setup = srv -> Script.base(srv, 4, false);
        speed(28.0, 0.35F);
        for (int i = 0; i < 4; i++) {
            client(28.1 + i * 0.7, () -> MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND));
        }
        cut(28.0, "fp", shot(p(0, 5, 5), p(0, 0, 0), 70));

        scene("fp_getsuga", 31.0, 34.0);
        s.firstPerson = true;
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 4, true);
        speed(31.0, 0.4F);
        server(31.2, srv -> Stage.getsuga(srv, 180.0F));
        cut(31.0, "fp", shot(p(0, 5, 5), p(0, 0, 0), 70));

        scene("fp_auto", 34.0, 36.0);
        s.firstPerson = true;
        s.setup = srv -> {
            ServerPlayerEntity p = Script.base(srv, 4, false);
            Stage.spawn(p, "B1", EntityType.ZOMBIE, Stage.at(0, 0, -4), 0.0F, false, 0.0F);
        };
        server(34.0, srv -> Stage.wake(srv, "B"));
        speed(34.0, 0.5F);
        cut(34.0, "fp", shot(p(0, 5, 5), p(0, 0, 0), 70));
    }

    // ---------------------------------------------------------------- словарь

    private void scene(String name, double start, double end) {
        this.s = new Scene(name, start, end);
        this.scenes.add(this.s);
    }

    private void cut(double time, String name, Shot shot) {
        this.s.cuts.add(new Scene.Cut(time, name, shot));
    }

    private void client(double time, Runnable action) {
        this.s.events.add(new Scene.Event(time, action));
    }

    private void server(double time, Consumer<MinecraftServer> action) {
        this.s.events.add(new Scene.Event(time, () -> Lockstep.onServer(() -> action.accept(MinecraftClient.getInstance().getServer()))));
    }

    private void speed(double time, float value) {
        this.s.speeds.put(time, value);
    }
}
