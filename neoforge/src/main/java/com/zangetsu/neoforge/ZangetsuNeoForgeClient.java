package com.zangetsu.neoforge;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.client.KeyBindings;
import com.zangetsu.client.WorldRenderInfo;
import com.zangetsu.client.ZangetsuClient;
import com.zangetsu.client.ZangetsuHud;
import com.zangetsu.network.ClientBridge;
import com.zangetsu.registry.ModItems;
import java.io.IOException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/** NeoForge, клиент: подключить общий клиент мода к событиям NeoForge. */
@Mod(value = ZangetsuMod.MOD_ID, dist = Dist.CLIENT)
public final class ZangetsuNeoForgeClient {
    public ZangetsuNeoForgeClient(IEventBus modBus) {
        ClientBridge.set(ZangetsuClient::receive);
        com.zangetsu.client.ClientNet.set(net.neoforged.neoforge.network.PacketDistributor::sendToServer);

        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> ZangetsuClient.registerEntityRenderers(event::registerEntityRenderer));
        modBus.addListener(EntityRenderersEvent.AddLayers.class, event -> {
            for (var model : event.getSkins()) {
                EntityRenderer<?> renderer = event.getSkin(model);
                if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                    ZangetsuClient.addPlayerFeatures(playerRenderer, playerRenderer::addFeature);
                }
            }
        });
        modBus.addListener(RegisterClientExtensionsEvent.class, event -> event.registerItem(new IClientItemExtensions() {
            private BuiltinModelItemRenderer renderer;

            @Override
            public BuiltinModelItemRenderer getCustomRenderer() {
                if (renderer == null) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    renderer = new BuiltinModelItemRenderer(client.getBlockEntityRenderDispatcher(), client.getEntityModelLoader()) {
                        @Override
                        public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
                            ZangetsuClient.ITEM_RENDERER.render(stack, mode, matrices, consumers, light, overlay);
                        }
                    };
                }
                return renderer;
            }
        }, ModItems.ZANGETSU));
        modBus.addListener(RegisterShadersEvent.class, event -> {
            try {
                event.registerShader(new ShaderProgram(event.getResourceProvider(), ZangetsuClient.YHWACH_SHADER, ZangetsuClient.YHWACH_FORMAT),
                        ZangetsuClient::shaderLoaded);
            } catch (IOException e) {
                ZangetsuMod.LOGGER.error("Не удалось загрузить шейдер Яхве", e);
            }
        });
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(KeyBindings.hollow));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(ZangetsuMod.id("hud"), ZangetsuHud::render));

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, event -> ZangetsuClient.clientTick(MinecraftClient.getInstance()));
        bus.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
                ZangetsuClient.beforeEntities(info(event));
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                ZangetsuClient.afterEntities(info(event));
            }
        });
        bus.addListener(EntityLeaveLevelEvent.class, event -> {
            if (event.getLevel().isClient) {
                ZangetsuClient.entityUnloaded(event.getEntity());
            }
        });
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> ZangetsuClient.disconnected());
    }

    private static WorldRenderInfo info(RenderLevelStageEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        return new WorldRenderInfo(client.world, event.getCamera(), event.getModelViewMatrix(), event.getPoseStack(),
                client.getBufferBuilders().getEntityVertexConsumers(), event.getPartialTick().getTickDelta(false));
    }
}
