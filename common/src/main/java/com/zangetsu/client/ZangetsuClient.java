package com.zangetsu.client;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.client.render.ArrowHalfRenderer;
import com.zangetsu.client.render.DreamSky;
import com.zangetsu.client.render.GetsugaRenderer;
import com.zangetsu.client.render.BentLimbFeatureRenderer;
import com.zangetsu.client.render.FinalFormFeatureRenderer;
import com.zangetsu.client.render.HollowFeatureRenderer;
import com.zangetsu.client.render.SeveredPartRenderer;
import com.zangetsu.client.render.SlashTrails;
import com.zangetsu.client.render.SwordMoves;
import com.zangetsu.client.render.SpiritRenderer;
import com.zangetsu.client.render.ThrownZangetsuRenderer;
import com.zangetsu.logic.Eye;
import com.zangetsu.logic.Awakening;
import com.zangetsu.logic.Calling;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.network.CallingPayload;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.network.HollowPayload;
import com.zangetsu.network.LimbPayload;
import com.zangetsu.network.PlayerStatePayload;
import com.zangetsu.network.ReactionPayload;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModEntities;
import com.zangetsu.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.CustomPayload;
import com.zangetsu.client.render.ZangetsuItemRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

/**
 * Клиент мода. Загрузчик подписывает эти методы на свои события: отрисовщики сущностей и слоёв игрока,
 * тик клиента, выход из мира, приём пакетов. Небо и облака сна рисует миксин {@link com.zangetsu.client.mixin.WorldRendererMixin}.
 */
public final class ZangetsuClient {
    /** Свой шейдер для модели Яхве. */
    public static final net.minecraft.util.Identifier YHWACH_SHADER = ZangetsuMod.id("yhwach");
    public static final net.minecraft.client.render.VertexFormat YHWACH_FORMAT = net.minecraft.client.render.VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL;
    /** Меч — объёмная 3D-модель со своим отрисовщиком и живым глазом. */
    public static final ZangetsuItemRenderer ITEM_RENDERER = new ZangetsuItemRenderer();

    private ZangetsuClient() {
    }

    public interface RendererSink {
        <E extends Entity> void register(EntityType<? extends E> type, EntityRendererFactory<E> factory);
    }

    public static void registerEntityRenderers(RendererSink sink) {
        sink.register(ModEntities.SPIRIT, SpiritRenderer::new);
        sink.register(ModEntities.THROWN_ZANGETSU, ThrownZangetsuRenderer::new);
        sink.register(ModEntities.SEVERED_PART, SeveredPartRenderer::new);
        sink.register(ModEntities.ARROW_HALF, ArrowHalfRenderer::new);
        sink.register(ModEntities.GETSUGA, GetsugaRenderer::new);
    }

    /** Слои поверх игрока: согнутые руки, дыра, финальная форма, маска Пустого. */
    public static void addPlayerFeatures(PlayerEntityRenderer playerRenderer,
            Consumer<FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>> add) {
        add.accept(new BentLimbFeatureRenderer(playerRenderer));
        add.accept(new com.zangetsu.client.render.HollowHoleRenderer(playerRenderer));
        add.accept(new FinalFormFeatureRenderer(playerRenderer));
        add.accept(new HollowFeatureRenderer(playerRenderer));
    }

    public static void shaderLoaded(net.minecraft.client.gl.ShaderProgram program) {
        com.zangetsu.client.render.YhwachModel.shader = program;
    }

    public static void clientTick(MinecraftClient client) {
        ClientState.ticks++;
        KeyBindings.tick(client);
        ReactionCheck.tick();
        CameraDirector.tick(client);
        ZangetsuCombat.tick(client);
    }

    /** Мир до сущностей: пустота сна. */
    public static void beforeEntities(WorldRenderInfo info) {
        com.zangetsu.client.render.DreamVoid.render(info);
    }

    /** Мир после сущностей: шлейфы ударов — светящиеся дуги в воздухе. */
    public static void afterEntities(WorldRenderInfo info) {
        SlashTrails.render(info);
    }

    public static void entityUnloaded(Entity entity) {
        ClientState.forget(entity.getId());
    }

    public static void disconnected() {
        ClientState.reset();
        CameraDirector.reset();
        ReactionCheck.reset();
        TooSlowScene.reset();
        CallingScene.reset();
        SlashTrails.clear();
    }

    /** Пакет с сервера. Зовётся в потоке клиента. */
    public static void receive(CustomPayload raw) {
        if (raw instanceof PlayerStatePayload payload) {
            ClientState.state = payload;
            return;
        }
        if (raw instanceof ReactionPayload payload) {
            ReactionCheck.receive(payload);
            return;
        }
        if (raw instanceof CallingPayload payload) {
            CallingScene.receive(payload);
            return;
        }
        if (raw instanceof LimbPayload payload) {
            if (payload.limbs() == 0) {
                ClientState.LIMBS.remove(payload.entityId());
            } else {
                ClientState.LIMBS.put(payload.entityId(), payload.limbs());
            }
            return;
        }
        if (raw instanceof HollowPayload payload) {
            if (payload.active()) {
                ClientState.HOLLOW.put(payload.entityId(), payload.crack());
                if (payload.finalForm()) {
                    ClientState.FINAL.add(payload.entityId());
                } else {
                    ClientState.FINAL.remove(payload.entityId());
                }
            } else {
                ClientState.HOLLOW.remove(payload.entityId());
                ClientState.FINAL.remove(payload.entityId());
            }
            return;
        }
        if (raw instanceof AnimationPayload payload) {
            MinecraftClient client = MinecraftClient.getInstance();
            Entity entity = client.world == null ? null : client.world.getEntityById(payload.entityId());
            if (entity == null) {
                return;
            }
            if (payload.animation() == AnimationPayload.CUT_MARK) {
                ClientState.MARKS.put(entity.getId(), entity.age);
                return;
            }
            if (entity instanceof PlayerEntity player && payload.animation() == AnimationPayload.AUTO_SLASH) {
                // Меч рубит сам: быстрый приём, повёрнутый к цели, с золотым следом.
                ZangetsuCombat.start(player, MathHelper.clamp(payload.param() * MathHelper.RADIANS_PER_DEGREE, -1.2F, 1.2F),
                        SwordMoves.auto(ClientState.nextSwing(player)), SwordMoves.AUTO_SPEED, SlashTrails.GOLD);
                return;
            }
            if (entity instanceof PlayerEntity player && (payload.animation() == AnimationPayload.EXECUTE || payload.animation() == AnimationPayload.EXECUTE_FLASH)) {
                ZangetsuCombat.execution(player, payload.param(), payload.duration());
                if (entity == client.player && payload.animation() == AnimationPayload.EXECUTE) {
                    CameraDirector.begin(payload.duration() + 12, new CameraDirector.Shot(65.0F, 95.0F, 10.0F, 4.6F, 1.3F));
                }
                return;
            }
            if (entity instanceof PlayerEntity player && payload.animation() == AnimationPayload.RAMPAGE_BEGIN) {
                RampageScene.begin(player, payload.duration(), payload.param());
                return;
            }
            if (entity instanceof PlayerEntity player && payload.animation() == AnimationPayload.RAMPAGE_END) {
                RampageScene.end(player);
                return;
            }
            if (entity instanceof PlayerEntity player && payload.animation() == AnimationPayload.EXECUTE_CHARGED) {
                ZangetsuCombat.charged(player, payload.param(), payload.duration(), com.zangetsu.logic.Execution.CHARGE_PAUSE);
                return;
            }
            ClientState.ANIMATIONS.put(payload.entityId(), new ClientState.Animation(payload.animation(), entity.age, payload.duration(), payload.param()));
            if (entity instanceof PlayerEntity player && payload.animation() == AnimationPayload.GETSUGA) {
                SlashTrails.getsuga(player, payload.duration() * 0.4F);
                ZangetsuCombat.flare(player, 1.2F, 0.3F);
                return;
            }
            if (entity == client.player && payload.animation() == AnimationPayload.EXECUTE_BEHIND) {
                TooSlowScene.begin(client.world.getEntityById(payload.param()));
                return;
            }
            if (entity == client.player && payload.animation() == AnimationPayload.HOLLOW) {
                // Лицо крупно, пока по нему идёт рука; с хлёстком рукой — отъезд, чтобы рука не вылетала из кадра.
                CameraDirector.begin(payload.duration() + 8,
                        new CameraDirector.Cut(0, new CameraDirector.Shot(214.0F, 198.0F, 2.0F, 1.95F, 1.6F, 0.0F, 0.2F)),
                        new CameraDirector.Cut(HollowForm.MASK_WHIP, new CameraDirector.Shot(198.0F, 180.0F, -3.0F, 2.0F, 3.4F, 0.0F, 0.0F)));
                return;
            }
            if (entity == client.player && payload.animation() == AnimationPayload.SWORD_BROKEN) {
                // Одним дублем: от плеча игрока на всю фигуру, к руке на клинке, к лицу, пока он говорит, снова к руке в миг перелома, и назад.
                float grip = com.zangetsu.logic.SwordBreak.GRIP, snap = com.zangetsu.logic.SwordBreak.SNAP, total = com.zangetsu.logic.SwordBreak.TOTAL;
                float arm = com.zangetsu.logic.SwordBreak.ARM_FROM, ready = com.zangetsu.logic.SwordBreak.READY, launch = com.zangetsu.logic.SwordBreak.LAUNCH, speech = com.zangetsu.logic.SwordBreak.SPEECH, crush = com.zangetsu.logic.SwordBreak.CRUSH;
                // Куда смотреть: грудь, кулак, лицо и висящий меч, вперёд и вверх от игрока.
                float chestAhead = 12.0F, chestUp = 15.6F, fistAhead = 5.3F, fistUp = 13.7F, faceAhead = 13.2F, faceUp = 19.6F, swordAhead = 1.75F, swordUp = 0.8F;
                CameraDirector.fly(payload.duration() + 4,
                        new CameraDirector.Key(0.0F, 38.0F, 8.0F, 5.0F, 0.4F, -0.1F),
                        new CameraDirector.Key(30.0F, 22.0F, -14.0F, 9.0F, 0.9F, 0.9F),
                        new CameraDirector.Key(arm - 8.0F, 20.0F, -26.0F, 22.0F, chestAhead, chestUp + 1.0F),
                        new CameraDirector.Key(arm + 24.0F, 26.0F, -20.0F, 18.0F, 11.0F, 15.0F),
                        new CameraDirector.Key(ready - 8.0F, 46.0F, -14.0F, 21.0F, 8.0F, 14.0F),
                        new CameraDirector.Key(ready + 6.0F, 52.0F, -12.0F, 21.0F, 7.0F, 13.5F),
                        new CameraDirector.Key(launch - 20.0F, 30.0F, -12.0F, 9.5F, swordAhead, swordUp),
                        new CameraDirector.Key(launch - 1.0F, 34.0F, -10.0F, 7.5F, swordAhead, swordUp + 0.3F),
                        new CameraDirector.Key(launch + 6.0F, 40.0F, -16.0F, 23.0F, 4.5F, 10.0F),
                        new CameraDirector.Key(grip, 36.0F, -4.0F, 11.0F, fistAhead, fistUp),
                        new CameraDirector.Key(grip + 22.0F, 24.0F, -3.0F, 9.0F, fistAhead, fistUp),
                        new CameraDirector.Key(speech - 6.0F, 10.0F, -6.0F, 10.0F, fistAhead + 0.5F, fistUp + 1.0F),
                        new CameraDirector.Key(speech + 16.0F, -26.0F, -22.0F, 16.0F, faceAhead, faceUp),
                        new CameraDirector.Key(crush - 20.0F, -34.0F, -24.0F, 12.0F, faceAhead, faceUp),
                        new CameraDirector.Key(crush - 4.0F, 6.0F, -6.0F, 12.5F, 5.6F, 14.3F),
                        new CameraDirector.Key(snap, 9.0F, -6.0F, 10.5F, 5.6F, 14.3F),
                        new CameraDirector.Key(snap + 12.0F, 11.0F, -8.0F, 15.0F, 5.6F, 14.6F),
                        new CameraDirector.Key(snap + 36.0F, 5.0F, -20.0F, 26.0F, 7.0F, 12.0F),
                        new CameraDirector.Key(total - 34.0F, 0.0F, -22.0F, 28.0F, 9.0F, 13.0F),
                        new CameraDirector.Key(total - 16.0F, 0.0F, -20.0F, 26.0F, 8.0F, 12.0F),
                        new CameraDirector.Key(total + 4.0F, 25.0F, 10.0F, 5.0F, 0.0F, 0.0F));
                CameraDirector.impacts(grip, 1.0F, snap, 1.35F);
                return;
            }
            if (entity == client.player && payload.animation() == AnimationPayload.TOUCH_HANDLE) {
                // Одним дублем: из-за плеча на подходе, вплотную к руке на рукояти, снизу на подъёме клинка и плавно вокруг, пока меч не ляжет на плечо.
                int walk = payload.duration() - Awakening.CLAIM_TICKS;
                CameraDirector.fly(payload.duration() + 6,
                        new CameraDirector.Key(0.0F, 32.0F, 9.0F, 3.4F, 0.45F, -0.05F),
                        new CameraDirector.Key(walk, 52.0F, 7.0F, 2.7F, 0.42F, -0.12F),
                        new CameraDirector.Key(walk + Awakening.CLAIM_REACH, 74.0F, 3.0F, 2.2F, 0.42F, -0.16F),
                        new CameraDirector.Key(walk + Awakening.CLAIM_GRIP + 6.0F, 94.0F, 0.0F, 2.5F, 0.36F, -0.18F),
                        new CameraDirector.Key(walk + 50.0F, 126.0F, -6.0F, 2.9F, 0.2F, -0.2F),
                        new CameraDirector.Key(walk + Awakening.CLAIM_CLEAR, 154.0F, -8.0F, 3.1F, 0.08F, -0.15F),
                        new CameraDirector.Key(walk + Awakening.CLAIM_SHOULDER - 6.0F, 188.0F, -5.0F, 3.2F, 0.0F, -0.05F),
                        new CameraDirector.Key(walk + Awakening.CLAIM_SHOULDER + 12.0F, 200.0F, -4.0F, 2.8F, 0.0F, 0.0F),
                        new CameraDirector.Key(walk + Awakening.CLAIM_HOLD_END, 197.0F, -3.0F, 2.35F, 0.0F, 0.0F),
                        new CameraDirector.Key(payload.duration() + 6.0F, 194.0F, -3.0F, 2.3F, 0.0F, 0.0F));
                return;
            }
            if (entity == client.player && payload.animation() == AnimationPayload.HOLLOW_FINAL) {
                // Как снято в кино: злость вблизи, молния широко, колено снизу со стороны меча, подъём снизу вверх, меч на плечо вполоборота, выпад сбоку.
                CameraDirector.begin(payload.duration() + 8,
                        new CameraDirector.Cut(0, new CameraDirector.Shot(205.0F, 188.0F, -3.0F, 3.1F, 2.2F, 0.0F, 0.0F)),
                        new CameraDirector.Cut(HollowForm.RITE_STRIKE - 2, new CameraDirector.Shot(190.0F, 195.0F, -12.0F, 7.5F, 7.0F, 0.0F, -0.2F)),
                        new CameraDirector.Cut(HollowForm.RITE_STRIKE + 12, new CameraDirector.Shot(208.0F, 196.0F, -5.0F, 3.7F, 3.3F, 0.0F, -0.65F)),
                        new CameraDirector.Cut(HollowForm.RITE_KNEEL_END, new CameraDirector.Shot(172.0F, 188.0F, -14.0F, 3.9F, 3.5F, 0.0F, -0.2F)),
                        new CameraDirector.Cut(HollowForm.RITE_STAND, new CameraDirector.Shot(218.0F, 200.0F, -2.0F, 3.1F, 2.8F, 0.0F, -0.05F)),
                        new CameraDirector.Cut(HollowForm.RITE_LUNGE - 8, new CameraDirector.Shot(262.0F, 275.0F, 2.0F, 4.6F, 4.2F, 1.2F, -0.3F)));
                return;
            }
            if (payload.animation() == AnimationPayload.RAMPAGE_DEATH || payload.animation() == AnimationPayload.RAMPAGE_STANCE) {
                // Камера этой сцены уже летит сама.
                return;
            }
            if (entity == client.player) {
                // У себя — камера облетает сбоку.
                CameraDirector.begin(payload.duration() + 8, switch (payload.animation()) {
                    case AnimationPayload.DREAM_PULL -> new CameraDirector.Shot(55.0F, 105.0F, 14.0F, 4.4F, 1.2F);
                    default -> new CameraDirector.Shot(90.0F, 90.0F, 10.0F, 4.0F, 0.0F);
                });
            }
            return;
        }
        if (raw instanceof ClientFxPayload payload) {
            MinecraftClient client = MinecraftClient.getInstance();
            switch (payload.effect()) {
                case ClientFxPayload.FLOATING_SWORD -> {
                    ItemStack sword = new ItemStack(ModItems.ZANGETSU);
                    sword.set(ModComponents.EYE, Eye.OPEN);
                    client.gameRenderer.showFloatingItem(sword);
                }
                case ClientFxPayload.CAMERA_KICK -> CameraDirector.kick(payload.duration() / 100.0F);
                case ClientFxPayload.WHITE_FLASH, ClientFxPayload.RED_FLASH, ClientFxPayload.IMPACT_FLASH -> ClientState.flash(payload.effect(), payload.duration());
                case ClientFxPayload.SHAKE -> {
                    if (client.player != null) {
                        client.player.maxHurtTime = 10;
                        client.player.hurtTime = 10;
                    }
                }
                default -> {
                }
            }
            return;
        }
    }
}
