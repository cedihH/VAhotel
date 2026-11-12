package com.mtchlvr.hotel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

@Service
public class RoomPhotoService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000L;
    private final Path root;
    public RoomPhotoService(@Value("${va.hotel.room-photo-dir:./uploads/rooms}") String dir) throws IOException {
        root = Paths.get(dir).toAbsolutePath().normalize(); Files.createDirectories(root);
    }

    public List<String> storeFive(List<MultipartFile> files) {
        if (files == null || files.size() != 5 || files.stream().anyMatch(f -> f == null || f.isEmpty()))
            throw new IllegalArgumentException("Upload exactly five room photos.");
        List<String> stored = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("Each room photo must be 5 MB or smaller.");
                String type = file.getContentType();
                if (!"image/jpeg".equalsIgnoreCase(type) && !"image/png".equalsIgnoreCase(type))
                    throw new IllegalArgumentException("Room photos must be JPEG or PNG images.");
                BufferedImage image = readValidated(file);
                String ext = "image/png".equalsIgnoreCase(type) ? ".png" : ".jpg";
                String name = UUID.randomUUID() + ext;
                Path target = root.resolve(name).normalize();
                if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid photo path.");
                if (!ImageIO.write(image, ext.equals(".png") ? "png" : "jpg", target.toFile())) throw new IllegalArgumentException("Unsupported photo encoding.");
                stored.add(name);
            }
            return List.copyOf(stored);
        } catch (IOException e) { throw new IllegalArgumentException("Could not store room photos.", e); }
    }

    private BufferedImage readValidated(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream(); ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) throw new IllegalArgumentException("Invalid room photo.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("Invalid room photo.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 400 || height < 300 || width > 10000 || height > 10000 || (long) width * height > MAX_PIXELS)
                    throw new IllegalArgumentException("Photos must be valid images at least 400 × 300 pixels and below 20 megapixels.");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }

    public byte[] load(String name) throws IOException {
        if (name == null || !name.matches("[a-fA-F0-9-]{36}\\.(jpg|png)")) throw new NoSuchFileException("photo");
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) throw new NoSuchFileException("photo");
        return Files.readAllBytes(target);
    }
    public String contentType(String name) { return name.endsWith(".png") ? "image/png" : "image/jpeg"; }
}
