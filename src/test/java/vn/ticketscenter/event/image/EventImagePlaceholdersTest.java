package vn.ticketscenter.event.image;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import vn.ticketscenter.storage.ImageContentValidator;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;

class EventImagePlaceholdersTest {
    @Test
    void categoryArtworkIsDecodableAndStable() throws Exception {
        byte[] music = EventImagePlaceholders.png("fixture-music");
        var image = ImageIO.read(new ByteArrayInputStream(music));
        assertEquals(1200, image.getWidth());
        assertEquals(675, image.getHeight());
        assertArrayEquals(
                music, ImageContentValidator.readAndValidate(new ByteArrayInputStream(music)));
        assertArrayEquals(music, EventImagePlaceholders.png("fixture-music"));
        assertFalse(Arrays.equals(music, EventImagePlaceholders.png("day-17-race")));
        assertFalse(Arrays.equals(music, EventImagePlaceholders.png(null)));
    }
}
