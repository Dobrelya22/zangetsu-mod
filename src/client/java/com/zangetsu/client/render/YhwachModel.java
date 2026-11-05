package com.zangetsu.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zangetsu.ZangetsuMod;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * Модель Яхве из Blender: части лежат в видеопамяти один раз, шейдер даёт рисованный свет и контур. Выгружает её tools/blender/export_game.py, там же описан формат файла.
 */
public final class YhwachModel {
    private static final Identifier FILE = ZangetsuMod.id("models/yhwach.bin");
    /** Текстуры частей: номер из файла минус один. */
    private static final Identifier[] TEXTURES = {
            ZangetsuMod.id("textures/entity/yhwach_face.png"),
            ZangetsuMod.id("textures/entity/yhwach_neck.png"),
            ZangetsuMod.id("textures/entity/yhwach_shirt.png"),
            ZangetsuMod.id("textures/entity/yhwach_cloak.png")};

    /** Шейдер регистрируется при старте клиента. */
    public static ShaderProgram shader;

    private record Part(VertexBuffer buffer, int texture) {
    }

    private static final Map<String, Part> PARTS = new HashMap<>();
    private static final List<float[]> EYES = new ArrayList<>();
    private static boolean loaded;

    private YhwachModel() {
    }

    /** Глаза: центр, ось вправо, ось вверх, нормаль, полуширина, полувысота. */
    public static List<float[]> eyes() {
        return EYES;
    }

    private static void load() {
        loaded = true;
        try (InputStream raw = MinecraftClient.getInstance().getResourceManager().getResource(FILE).orElseThrow().getInputStream();
                DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw), 1 << 16))) {
            int parts = in.readInt();
            VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL;
            for (int p = 0; p < parts; p++) {
                String name = in.readUTF();
                int texture = in.readByte();
                float minX = in.readFloat(), minY = in.readFloat(), minZ = in.readFloat(), scale = in.readFloat();
                int vc = in.readInt();
                float[] pos = new float[vc * 3];
                float[] nor = new float[vc * 3];
                float[] uv = new float[vc * 2];
                int[] col = new int[vc];
                for (int i = 0; i < vc; i++) {
                    pos[i * 3] = minX + (in.readShort() + 32768) / 65535.0F * scale;
                    pos[i * 3 + 1] = minY + (in.readShort() + 32768) / 65535.0F * scale;
                    pos[i * 3 + 2] = minZ + (in.readShort() + 32768) / 65535.0F * scale;
                    nor[i * 3] = in.readByte() / 127.0F;
                    nor[i * 3 + 1] = in.readByte() / 127.0F;
                    nor[i * 3 + 2] = in.readByte() / 127.0F;
                    col[i] = in.readInt();
                    if (texture > 0) {
                        uv[i * 2] = in.readUnsignedShort() / 65535.0F;
                        uv[i * 2 + 1] = in.readUnsignedShort() / 65535.0F;
                    }
                }
                int qc = in.readInt();
                try (BufferAllocator memory = new BufferAllocator(Math.max(256, qc * 4 * format.getVertexSizeByte()))) {
                    BufferBuilder builder = new BufferBuilder(memory, VertexFormat.DrawMode.QUADS, format);
                    int last = 0;
                    for (int q = 0; q < qc * 4; q++) {
                        last += in.readInt();
                        int i = last;
                        builder.vertex(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]).color(col[i]).texture(uv[i * 2], uv[i * 2 + 1]).light(0).normal(nor[i * 3], nor[i * 3 + 1], nor[i * 3 + 2]);
                    }
                    VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    buffer.bind();
                    buffer.upload(builder.end());
                    VertexBuffer.unbind();
                    PARTS.put(name, new Part(buffer, texture));
                }
            }
            int eyes = in.readInt();
            for (int e = 0; e < eyes; e++) {
                float[] eye = new float[14];
                for (int k = 0; k < 14; k++) {
                    eye[k] = in.readFloat();
                }
                EYES.add(eye);
            }
        } catch (Exception e) {
            ZangetsuMod.LOGGER.error("Зангецу: не удалось загрузить модель Яхве", e);
        }
        for (Identifier id : TEXTURES) {
            smooth(id);
        }
    }

    /** Без этого рисунок издали рябит, а вблизи виден по пикселям. */
    private static void smooth(Identifier id) {
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(id);
        GlStateManager._bindTexture(texture.getGlId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 6);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        texture.setFilter(true, true);
    }

    /** Подготовка к рисованию модели в этом кадре: свет в осях вида и начальные настройки. */
    public static boolean begin(Matrix4f worldToView, Vector3f worldLight, float time) {
        if (!loaded) {
            load();
        }
        if (shader == null) {
            return false;
        }
        RenderSystem.setShader(() -> shader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        Vector3f view = worldToView.transformDirection(new Vector3f(worldLight)).normalize();
        set("LightDir", view.x, view.y, view.z);
        set("Time", time);
        set("Reveal", 1.0F);
        set("Glow", 0.0F);
        set("Sway", 0.0F);
        set("Outline", 0.0F);
        set("ViewOutline", 0.0F);
        set("Bend", 0.0F);
        return true;
    }

    public static void end() {
        GL11.glCullFace(GL11.GL_BACK);
        VertexBuffer.unbind();
        RenderSystem.enableCull();
    }

    public static void set(String name, float a) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(a);
        }
    }

    public static void set(String name, float a, float b, float c) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(a, b, c);
        }
    }

    public static void set(String name, float a, float b, float c, float d) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(new Vector4f(a, b, c, d));
        }
    }

    /** Рисованный свет материала: где граница, насколько мягкая, чем красит тень. */
    public static void material(float threshold, float softness, float r, float g, float b) {
        set("Threshold", threshold);
        set("Softness", softness);
        set("ShadowTint", r, g, b);
    }

    private static VertexBuffer prepare(String name) {
        Part part = PARTS.get(name);
        if (part == null) {
            return null;
        }
        if (part.texture() > 0) {
            RenderSystem.setShaderTexture(0, TEXTURES[part.texture() - 1]);
            set("UseTexture", 1.0F);
        } else {
            set("UseTexture", 0.0F);
        }
        part.buffer().bind();
        return part.buffer();
    }

    private static void hull(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection, float thickness) {
        set("Outline", thickness);
        RenderSystem.enableCull();
        GL11.glCullFace(GL11.GL_FRONT);
        buffer.draw(modelView, projection, shader);
        GL11.glCullFace(GL11.GL_BACK);
        set("Outline", 0.0F);
    }

    /** Нарисовать часть. {@code outline} больше нуля: сначала чёрный контур, вывернутая копия. {@code twoSided}: открытая ткань, видна и с изнанки. */
    public static void draw(String name, Matrix4f modelView, Matrix4f projection, float outline, boolean twoSided) {
        VertexBuffer buffer = prepare(name);
        if (buffer == null) {
            return;
        }
        if (outline > 0.0F) {
            set("OutlineColor", 0.01F, 0.005F, 0.01F, 1.0F);
            hull(buffer, modelView, projection, outline);
        }
        if (twoSided) {
            RenderSystem.disableCull();
        } else {
            RenderSystem.enableCull();
        }
        buffer.draw(modelView, projection, shader);
    }

    /** Дальше часть гнётся в локте: {@code bone} переводит точку плеча в точку предплечья. */
    public static void bend(Matrix4f bone) {
        GlUniform uniform = shader.getUniform("Bone");
        if (uniform != null) {
            uniform.set(bone);
        }
        set("Bend", 1.0F);
    }

    public static void straight() {
        set("Bend", 0.0F);
    }

    /** Рука и перчатка: та же кайма по силуэту, что у плаща, иначе чёрное на чёрном не прочитать. */
    public static void drawRimmed(String name, Matrix4f modelView, Matrix4f projection, float band, float ink, float r, float g, float b, boolean twoSided) {
        VertexBuffer buffer = prepare(name);
        if (buffer == null) {
            return;
        }
        set("ViewOutline", 2.0F);
        set("OutlineColor", 0.01F, 0.005F, 0.01F, 1.0F);
        hull(buffer, modelView, projection, ink);
        set("OutlineColor", r, g, b, 1.0F);
        hull(buffer, modelView, projection, band);
        set("ViewOutline", 0.0F);
        if (twoSided) {
            RenderSystem.disableCull();
        } else {
            RenderSystem.enableCull();
        }
        buffer.draw(modelView, projection, shader);
    }

    /** Плащ: снаружи чёрная линия, под ней винная кайма. Ширина зависит от взгляда, считается в шейдере. */
    public static void drawCloth(String name, Matrix4f modelView, Matrix4f projection, float band, float ink, float r, float g, float b) {
        VertexBuffer buffer = prepare(name);
        if (buffer == null) {
            return;
        }
        set("ViewOutline", 1.0F);
        set("OutlineColor", 0.01F, 0.005F, 0.01F, 1.0F);
        hull(buffer, modelView, projection, ink);
        set("OutlineColor", r, g, b, 1.0F);
        hull(buffer, modelView, projection, band);
        set("ViewOutline", 0.0F);
        RenderSystem.disableCull();
        buffer.draw(modelView, projection, shader);
    }
}
