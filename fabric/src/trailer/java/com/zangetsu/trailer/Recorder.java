package com.zangetsu.trailer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zangetsu.ZangetsuMod;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
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
import org.lwjgl.opengl.GL30;

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
    private static boolean downsample;
    private static int sourceWidth;
    private static int sourceHeight;
    private static int halfFbo;
    private static ExecutorService stillWriter;
    private static Path stillDir;

    private Recorder() {
    }

    // ---------------------------------------------------------------- видео

    public static void startVideo(Path file, int w, int h) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        width = w;
        height = h;
        FREE.clear();
        FULL.clear();
        // Дубли снимаются вдвое крупнее: видеокарта усредняет каждые четыре точки в одну, края становятся чистыми.
        boolean takes = Director.FPS != 60;
        downsample = takes && w >= 3840;
        width = downsample ? w / 2 : w;
        height = downsample ? h / 2 : h;
        sourceWidth = w;
        sourceHeight = h;
        w = width;
        h = height;
        // Дубли для монтажа: быстрый кодек видеокарты, размер как снято, почти без потерь, частые опорные кадры.
        ProcessBuilder builder = Director.FPS != 60
                ? new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "warning", "-y",
                        "-f", "rawvideo", "-pix_fmt", "rgba", "-video_size", w + "x" + h, "-framerate", String.valueOf(Director.FPS), "-i", "pipe:0",
                        "-vf", "vflip,format=yuv444p",
                        "-c:v", "hevc_nvenc", "-profile:v", "rext", "-preset", "p4", "-tune", "hq", "-rc", "constqp", "-qp", "14", "-bf", "0", "-g", "48", file.toAbsolutePath().toString())
                : new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "warning", "-y",
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
            // Большими кусками: стандартный канал гонит по 8 КБ, и на этом всё и упирается.
            byte[] chunk = new byte[1 << 22];
            try (OutputStream out = ffmpeg.getOutputStream()) {
                while (true) {
                    ByteBuffer buffer = FULL.take();
                    if (buffer == END) {
                        break;
                    }
                    buffer.rewind();
                    while (buffer.hasRemaining()) {
                        int n = Math.min(chunk.length, buffer.remaining());
                        buffer.get(chunk, 0, n);
                        out.write(chunk, 0, n);
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
        if (framebuffer.textureWidth != sourceWidth || framebuffer.textureHeight != sourceHeight) {
            if (!sizeWarned) {
                sizeWarned = true;
                ZangetsuMod.LOGGER.error("Трейлер: кадр {}x{} вместо {}x{}", framebuffer.textureWidth, framebuffer.textureHeight, sourceWidth, sourceHeight);
            }
        }
        try {
            ByteBuffer buffer = FREE.take();
            buffer.clear();
            if (framebuffer.textureWidth == sourceWidth && framebuffer.textureHeight == sourceHeight) {
                if (downsample) {
                    readHalf(framebuffer, buffer);
                } else {
                    read(framebuffer, buffer);
                }
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

    /** Кадр вдвое меньше: линейная выборка ровно посередине четырёх точек и есть их среднее. */
    private static void readHalf(Framebuffer framebuffer, ByteBuffer buffer) {
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        if (halfFbo == 0) {
            halfFbo = GL30.glGenFramebuffers();
            int texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, halfFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            RenderSystem.bindTexture(0);
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, halfFbo);
        GL30.glBlitFramebuffer(0, 0, sourceWidth, sourceHeight, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, halfFbo);
        GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
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
