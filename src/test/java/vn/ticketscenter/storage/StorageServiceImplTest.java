package vn.ticketscenter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageServiceImplTest {
    @TempDir Path directory;
    private byte[] image(String format, int color) throws IOException {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color);
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, bytes));
        return bytes.toByteArray();
    }
    @Test void imageSurvivesRestartAndImmutableRetryDoesNotOverwriteIt() throws Exception {
        var storage = new StorageServiceImpl(directory, "/ticketscenter");
        String id = "ticketcenter/events/test/immutable";
        byte[] bytes = image("png", 0xffffff);
        var first = storage.ensure(new ByteArrayInputStream(bytes), id);
        assertTrue(StorageServiceImpl.isLocalUrl(first.secureUrl()));
        var restarted = new StorageServiceImpl(directory, "/ticketscenter");
        assertEquals(first, restarted.find(id).orElseThrow());
        assertEquals(first, restarted.ensure(new ByteArrayInputStream(bytes), id));
        assertThrows(IOException.class, () -> restarted.ensure(new ByteArrayInputStream(image("jpg", 0)), id));
        String filename = first.secureUrl().substring(first.secureUrl().lastIndexOf('/') + 1);
        assertArrayEquals(bytes, Files.readAllBytes(restarted.file(filename).orElseThrow()));
        restarted.delete(id);
        assertTrue(restarted.find(id).isEmpty());
    }
    @Test void jpegIsPreservedAndDiskFailuresAreReported() throws Exception {
        byte[] jpeg = image("jpg", 0);
        var storage = new StorageServiceImpl(directory, "");
        var asset = storage.ensure(new ByteArrayInputStream(jpeg), "test/jpeg");
        assertArrayEquals(jpeg, Files.readAllBytes(storage.file(asset.secureUrl().substring(StorageServiceImpl.ROUTE.length())).orElseThrow()));
        Path blocked = Files.writeString(directory.resolve("not-a-directory"), "blocked");
        assertThrows(IOException.class, () -> new StorageServiceImpl(blocked, "").ensure(new ByteArrayInputStream(jpeg), "test/failure"));
    }
    @Test void invalidPathsAndNonImagesAreRejected() throws Exception {
        var storage = new StorageServiceImpl(directory, "/ticketscenter");
        assertTrue(storage.file("../config.properties").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> storage.find("../event"));
        assertThrows(IllegalArgumentException.class, () -> storage.ensure(new ByteArrayInputStream("<svg/>".getBytes()), "test/svg"));
        for (String url : new String[]{"//evil.example/assets/event-images/" + "a".repeat(64) + ".img", "/assets/event-images/../config", "/ticketscenter/assets/event-images/" + "a".repeat(64) + ".img?x=1"})
            assertFalse(StorageServiceImpl.isLocalUrl(url));
    }
}
