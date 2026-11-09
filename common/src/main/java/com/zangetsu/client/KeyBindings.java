package com.zangetsu.client;

import com.zangetsu.network.ActionPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Клавиша мода: H — форма Пустого. Казнь теперь не кнопка, а проверка реакции ({@link ReactionCheck}). */
public final class KeyBindings {
    /** Регистрирует загрузчик: Fabric — через KeyBindingHelper, NeoForge — в RegisterKeyMappingsEvent. */
    public static final KeyBinding hollow = new KeyBinding("key.zangetsu.hollow", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H,
            "key.categories.zangetsu");

    private KeyBindings() {
    }

    static void tick(MinecraftClient client) {
        while (hollow.wasPressed()) {
            if (ClientState.state.bonded()) {
                ClientNet.send(new ActionPayload(ActionPayload.HOLLOW, 0));
            }
        }
    }
}
