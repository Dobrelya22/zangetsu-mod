package com.zangetsu.trailer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zangetsu.ZangetsuMod;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;

/**
 * Запись кадров. Видео: сырые кадры по трубе в ffmpeg (кадр в кадр, 60 в секунду). Стоп-кадры: JPEG в
 * половину размера — чтобы быстро посмотреть планы, не собирая видео.
 */
public final class Recorder {
    private static final int BUFFERS = 4;
    private static final ByteBuffer END = ByteBuffer.allocate(0);
    private static final BlockingQueue<ByteBuffer> FULL = new ArrayBlockingQueue<>(BUFFERS + 1);
    private static final BlockingQueue<ByteBuffer> FREE = new ArrayBlockingQueue<>(BUFFERS);
    private static Process ffmpeg;
    private static Thread writer;
    private static int width;
    private static int height;
    private static boolean sizeWarned;
    private static ExecutorService stillWriter;
    private static Path stillDir;

    private Recorder() {
    }

    // ---------------------------------------------------------------- видео

    public static void startVideo(Path file, int w, int h) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        width = w;
        height = h;
        ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "warning", "-y",
                "-f", "rawvideo", "-pix_fmt", "rgba", "-video_size", w + "x" + h, "-framerate", "60", "-i", "pipe:0",
                "-vf", "vflip,scale=1920:1080:flags=lanczos,format=yuv420p",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "12", "-g", "120", file.toAbsolutePath().toString());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(file.resolveSibling(file.getFileName() + ".log").toFile());
        ffmpeg = builder.start();
        for (int i = 0; i < BUFFERS; i++) {
            FREE.add(ByteBuffer.allocateDirect(w * h * 4));
        }
        writer = new Thread(() -> {
            try (WritableByteChannel channel = Channels.newChannel(ffmpeg.getOutputStream())) {
                while (true) {
                    ByteBuffer buffer = FULL.take();
                    if (buffer == END) {
                        break;
                    }
                    buffer.rewind();
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    FREE.add(buffer);
                }
            } catch (IOException | InterruptedException e) {
                ZangetsuMod.LOGGER.error("Трейлер: запись видео оборвалась", e);
            }
        }, "trailer-video");
        writer.start();
        ZangetsuMod.LOGGER.info("Трейлер: пишем {} ({}x{})", file, w, h);
    }

    public static boolean recordingVideo() {
        return ffmpeg != null;
    }

    public static void captureVideo(Framebuffer framebuffer) {
        if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
            if (!sizeWarned) {
                sizeWarned = true;
                ZangetsuMod.LOGGER.error("Трейлер: кадр {}x{} вместо {}x{}", framebuffer.textureWidth, framebuffer.textureHeight, width, height);
            }
        }
        try {
            ByteBuffer buffer = FREE.take();
            buffer.clear();
            if (framebuffer.textureWidth == width && framebuffer.textureHeight == height) {
                read(framebuffer, buffer);
            }
            FULL.put(buffer);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void finishVideo() {
        if (ffmpeg == null) {
            return;
        }
        try {
            FULL.put(END);
            writer.join();
            if (!ffmpeg.waitFor(10, TimeUnit.MINUTES)) {
                ZangetsuMod.LOGGER.error("Трейлер: ffmpeg не закончил за 10 минут");
            }
            ZangetsuMod.LOGGER.info("Трейлер: видео записано, ffmpeg вернул {}", ffmpeg.exitValue());
        } catch (InterruptedException | IllegalThreadStateException e) {
            ZangetsuMod.LOGGER.error("Трейлер: не дождались ffmpeg", e);
        }
        ffmpeg = null;
    }

    private static void read(Framebuffer framebuffer, ByteBuffer buffer) {
        RenderSystem.bindTexture(framebuffer.getColorAttachment());
        GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
    }

    // ---------------------------------------------------------------- стоп-кадры

    public static void startStills(Path dir) throws IOException {
        stillDir = dir;
        Files.createDirectories(dir);
        stillWriter = Executors.newSingleThreadExecutor(r -> new Thread(r, "trailer-stills"));
    }

    public static boolean recordingStills() {
        return stillWriter != null;
    }

    public static void captureStill(Framebuffer framebuffer, String name) {
        int w = framebuffer.textureWidth, h = framebuffer.textureHeight;
        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4);
        read(framebuffer, buffer);
        stillWriter.execute(() -> {
            // Половина размера; в OpenGL строки снизу вверх.
            int ow = w / 2, oh = h / 2;
            BufferedImage image = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < oh; y++) {
                int row = (h - 1 - y * 2) * w;
                for (int x = 0; x < ow; x++) {
                    int i = (row + x * 2) * 4;
                    int r = buffer.get(i) & 0xFF, g = buffer.get(i + 1) & 0xFF, b = buffer.get(i + 2) & 0xFF;
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            try {
                ImageIO.write(image, "jpg", stillDir.resolve(name + ".jpg").toFile());
            } catch (IOException e) {
                ZangetsuMod.LOGGER.error("Трейлер: стоп-кадр {} не записан", name, e);
            }
        });
    }

    public static void finishStills() {
        if (stillWriter == null) {
            return;
        }
        stillWriter.shutdown();
        try {
            stillWriter.awaitTermination(2, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        stillWriter = null;
    }
}
