package com.zangetsu.client;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.client.render.ArrowHalfRenderer;
import com.zangetsu.client.render.GetsugaRenderer;
import com.zangetsu.client.render.FinalFormFeatureRenderer;
import com.zangetsu.client.render.HollowFeatureRenderer;
import com.zangetsu.client.render.SeveredPartRenderer;
import com.zangetsu.client.render.SlashTrails;
import com.zangetsu.client.render.SwordMoves;
import com.zangetsu.client.render.SpiritModel;
import com.zangetsu.client.render.SpiritRenderer;
import com.zangetsu.client.render.ThrownZangetsuRenderer;
import com.zangetsu.logic.Eye;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.network.HollowPayload;
import com.zangetsu.network.LimbPayload;
import com.zangetsu.network.PlayerStatePayload;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModEntities;
import com.zangetsu.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import com.zangetsu.client.render.ZangetsuItemRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

public class ZangetsuClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(SpiritModel.LAYER, SpiritModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.SPIRIT, SpiritRenderer::new);
        EntityRendererRegistry.register(ModEntities.THROWN_ZANGETSU, ThrownZangetsuRenderer::new);
        EntityRendererRegistry.register(ModEntities.SEVERED_PART, SeveredPartRenderer::new);
        EntityRendererRegistry.register(ModEntities.ARROW_HALF, ArrowHalfRenderer::new);
        EntityRendererRegistry.register(ModEntities.GETSUGA, GetsugaRenderer::new);

        // Меч — объёмная 3D-модель со своим отрисовщиком и живым глазом.
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.ZANGETSU, new ZangetsuItemRenderer());

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                helper.register(new FinalFormFeatureRenderer(playerRenderer));
                helper.register(new HollowFeatureRenderer(playerRenderer));
            }
        });

        KeyBindings.register();
        HudRenderCallback.EVENT.register(ZangetsuHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientState.ticks++;
            KeyBindings.tick(client);
            CameraDirector.tick(client);
            ZangetsuCombat.tick(client);
        });
        // Шлейфы ударов — светящиеся дуги в воздухе.
        WorldRenderEvents.AFTER_ENTITIES.register(SlashTrails::render);
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> ClientState.forget(entity.getId()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientState.reset();
            CameraDirector.reset();
            SlashTrails.clear();
        });

        registerReceivers();
    }

    private static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(PlayerStatePayload.ID, (payload, context) -> ClientState.state = payload);
        ClientPlayNetworking.registerGlobalReceiver(LimbPayload.ID, (payload, context) -> {
            if (payload.limbs() == 0) {
                ClientState.LIMBS.remove(payload.entityId());
            } else {
                ClientState.LIMBS.put(payload.entityId(), payload.limbs());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(HollowPayload.ID, (payload, context) -> {
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
        });
        ClientPlayNetworking.registerGlobalReceiver(AnimationPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            Entity entity = client.world == null ? null : client.world.getEntityById(payload.entityId());
            if (entity == null) {
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
            ClientState.ANIMATIONS.put(payload.entityId(), new ClientState.Animation(payload.animation(), entity.age, payload.duration(), payload.param()));
            if (entity instanceof PlayerEntity player && payload.animation() == AnimationPayload.GETSUGA) {
                SlashTrails.getsuga(player, payload.duration() * 0.4F);
                ZangetsuCombat.flare(player, 1.2F, 0.3F);
                return;
            }
            if (entity == client.player && payload.animation() == AnimationPayload.HOLLOW) {
                // Лицо крупно, пока по нему идёт рука; когда он поднимает голову — отъезд.
                CameraDirector.begin(payload.duration() + 8,
                        new CameraDirector.Cut(0, new CameraDirector.Shot(214.0F, 198.0F, 2.0F, 1.95F, 1.6F, 0.0F, 0.2F)),
                        new CameraDirector.Cut(HollowForm.MASK_ROAR, new CameraDirector.Shot(198.0F, 176.0F, -3.0F, 1.7F, 3.2F, 0.0F, 0.05F)));
                return;
            }
            if (entity == client.player && payload.animation() == AnimationPayload.HOLLOW_FINAL) {
                // Как снято в кино: злость вблизи, молния широко, колено снизу со стороны меча, подъём снизу вверх, меч на плечо вполоборота, выпад сбоку.
                CameraDirector.begin(payload.duration() + 8,
                        new CameraDirector.Cut(0, new CameraDirector.Shot(205.0F, 188.0F, -3.0F, 3.1F, 2.2F, 0.0F, 0.0F)),
                        new CameraDirector.Cut(HollowForm.RITE_STRIKE - 2, new CameraDirector.Shot(190.0F, 195.0F, -12.0F, 7.5F, 7.0F, 0.0F, -0.2F)),
                        new CameraDirector.Cut(HollowForm.RITE_STRIKE + 12, new CameraDirector.Shot(208.0F, 196.0F, -5.0F, 3.7F, 3.3F, 0.0F, -0.6F)),
                        new CameraDirector.Cut(HollowForm.RITE_KNEEL_END, new CameraDirector.Shot(172.0F, 188.0F, -14.0F, 3.9F, 3.5F, 0.0F, -0.2F)),
                        new CameraDirector.Cut(HollowForm.RITE_STAND, new CameraDirector.Shot(218.0F, 200.0F, -2.0F, 3.1F, 2.8F, 0.0F, -0.05F)),
                        new CameraDirector.Cut(HollowForm.RITE_LUNGE - 8, new CameraDirector.Shot(262.0F, 275.0F, 2.0F, 4.6F, 4.2F, 1.2F, -0.3F)));
                return;
            }
            if (entity == client.player) {
                // У себя — камера облетает сбоку.
                CameraDirector.begin(payload.duration() + 8, switch (payload.animation()) {
                    case AnimationPayload.TOUCH_HANDLE -> new CameraDirector.Shot(60.0F, 90.0F, 14.0F, 4.0F, 1.2F);
                    case AnimationPayload.DREAM_PULL -> new CameraDirector.Shot(55.0F, 105.0F, 14.0F, 4.4F, 1.2F);
                    default -> new CameraDirector.Shot(90.0F, 90.0F, 10.0F, 4.0F, 0.0F);
                });
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ClientFxPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            switch (payload.effect()) {
                case ClientFxPayload.FLOATING_SWORD -> {
                    ItemStack sword = new ItemStack(ModItems.ZANGETSU);
                    sword.set(ModComponents.EYE, Eye.OPEN);
                    client.gameRenderer.showFloatingItem(sword);
                }
                case ClientFxPayload.WHITE_FLASH, ClientFxPayload.RED_FLASH -> ClientState.flash(payload.effect(), payload.duration());
                case ClientFxPayload.SHAKE -> {
                    if (client.player != null) {
                        client.player.maxHurtTime = 10;
                        client.player.hurtTime = 10;
                    }
                }
                default -> {
                }
            }
        });
    }
}
