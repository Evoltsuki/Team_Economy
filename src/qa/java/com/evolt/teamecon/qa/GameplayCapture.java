package com.evolt.teamecon.qa;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Optional framebuffer recording for release footage. This source set is never shipped. */
final class GameplayCapture {
    private static final int FPS = 30;
    private static Process process;
    private static BufferedOutputStream input;
    private static Path clip;
    private static int width, height, frames;
    private static long started;

    private GameplayCapture() {}

    static void start(Path directory, String name) {
        if (process != null) throw new IllegalStateException("A capture is already running");
        String encoder = System.getProperty("teamecon.qaVideoEncoder", "");
        if (encoder.isBlank() || !Files.isRegularFile(Path.of(encoder)))
            throw new IllegalStateException("Showcase recording requires -PqaVideoEncoder=<ffmpeg executable>");
        try {
            Files.createDirectories(directory);
            clip = directory.resolve(name + ".mp4");
            var window = Minecraft.getInstance().getWindow();
            width = window.getWidth(); height = window.getHeight(); frames = 0;
            process = new ProcessBuilder(encoder, "-y", "-hide_banner", "-loglevel", "warning",
                    "-f", "rawvideo", "-pixel_format", "rgba", "-video_size", width + "x" + height,
                    "-framerate", String.valueOf(FPS), "-i", "pipe:0", "-an",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "19",
                    "-pix_fmt", "yuv420p", "-movflags", "+faststart", clip.toString())
                    .redirectError(directory.resolve(name + ".encoder.log").toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            input = new BufferedOutputStream(process.getOutputStream(), 1024 * 1024);
            started = System.nanoTime();
        } catch (IOException ex) { throw new IllegalStateException("Could not start gameplay recording", ex); }
    }

    static void frame() {
        if (process == null) return;
        int wanted = (int) ((System.nanoTime() - started) * FPS / 1_000_000_000L) + 1;
        if (frames >= wanted) return;
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            if (image.getWidth() != width || image.getHeight() != height)
                throw new IllegalStateException("Window resized during gameplay recording");
            int[] pixels = image.getPixelsRGBA();
            byte[] rgba = new byte[pixels.length * 4];
            int offset = 0;
            for (int pixel : pixels) {
                rgba[offset++] = (byte) pixel;
                rgba[offset++] = (byte) (pixel >>> 8);
                rgba[offset++] = (byte) (pixel >>> 16);
                rgba[offset++] = (byte) (pixel >>> 24);
            }
            // Preserve elapsed time if rendering briefly pauses, without inventing animation frames.
            while (frames < wanted) { input.write(rgba); frames++; }
        } catch (IOException ex) { throw new IllegalStateException("Gameplay encoder stopped", ex); }
    }

    static void stop() {
        if (process == null) return;
        Process running = process;
        process = null;
        try {
            input.close(); input = null;
            if (!running.waitFor(20, TimeUnit.SECONDS)) {
                running.destroyForcibly();
                throw new IllegalStateException("Gameplay encoder did not finish");
            }
            if (running.exitValue() != 0 || frames == 0)
                throw new IllegalStateException("Invalid recorded clip: " + clip);
            Files.writeString(clip.resolveSibling(clip.getFileName() + ".json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                            "frames", frames, "fps", FPS, "duration_seconds", frames / (double) FPS,
                            "width", width, "height", height, "gameplay_recording", true,
                            "source", "Minecraft framebuffer; staged creative demonstration world",
                            "random_outcomes", "Normal game RNG; no forced winnings")));
        } catch (IOException | InterruptedException ex) {
            running.destroyForcibly();
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not finish gameplay recording", ex);
        }
    }
}
