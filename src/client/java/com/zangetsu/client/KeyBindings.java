package com.zangetsu.client;

import com.zangetsu.network.ActionPayload;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Клавиша мода: H — форма Пустого. Казнь теперь не кнопка, а проверка реакции ({@link ReactionCheck}). */
public final class KeyBindings {
    public static KeyBinding hollow;

    private KeyBindings() {
    }

    static void register() {
        hollow = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.zangetsu.hollow", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H,
                "key.categories.zangetsu"));
    }

    static void tick(MinecraftClient client) {
        while (hollow.wasPressed()) {
            if (ClientState.state.bonded()) {
                ClientPlayNetworking.send(new ActionPayload(ActionPayload.HOLLOW, 0));
            }
        }
    }
}
