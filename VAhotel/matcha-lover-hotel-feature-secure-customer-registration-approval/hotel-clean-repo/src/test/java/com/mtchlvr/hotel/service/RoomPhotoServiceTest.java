package com.mtchlvr.hotel.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoomPhotoServiceTest {
    @TempDir Path temporaryDirectory;

    @Test void storesAndReloadsExactlyFiveValidatedPhotos() throws Exception {
        RoomPhotoService service = new RoomPhotoService(temporaryDirectory.toString());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "jpg", bytes);
        byte[] jpeg = bytes.toByteArray();
        List<org.springframework.web.multipart.MultipartFile> photos = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> (org.springframework.web.multipart.MultipartFile) new MockMultipartFile("photos", "photo.jpg", "image/jpeg", jpeg)).toList();
        List<String> stored = service.storeFive(photos);
        assertEquals(5, stored.size());
        for (String name : stored) {
            assertEquals("image/jpeg", service.contentType(name));
            assertTrue(service.load(name).length > 0);
        }
    }

    @Test void rejectsASetThatDoesNotContainFivePhotos() throws Exception {
        RoomPhotoService service = new RoomPhotoService(temporaryDirectory.toString());
        assertThrows(IllegalArgumentException.class, () -> service.storeFive(List.of()));
    }
}
