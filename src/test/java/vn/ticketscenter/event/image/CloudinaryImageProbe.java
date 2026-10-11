package vn.ticketscenter.event.image;

import vn.ticketscenter.storage.CloudinaryStorageService;
import vn.ticketscenter.storage.StorageService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

public final class CloudinaryImageProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("verify-live-provider")) {
            throw new IllegalArgumentException(
                    "Pass verify-live-provider to allocate and remove one test image");
        }
        StorageService storage = CloudinaryStorageService.fromEnvironment();
        String id = "ticketcenter/events/" + UUID.randomUUID() + "/" + UUID.randomUUID();
        byte[] image = EventImagePlaceholders.png("music");
        try {
            var first = storage.ensure(new ByteArrayInputStream(image), id);
            var second = storage.ensure(new ByteArrayInputStream(image), id);
            if (!first.equals(second) || storage.find(id).isEmpty())
                throw new IllegalStateException("Repeated upload changed the asset");
            boolean conflictRejected = false;
            try {
                storage.ensure(new ByteArrayInputStream(EventImagePlaceholders.png("sport")), id);
            } catch (IOException expected) {
                conflictRejected = true;
            }
            if (!conflictRejected)
                throw new IllegalStateException(
                        "Different bytes were accepted for an immutable image ID");
            var decoded = EventImageMigration.download(first.secureUrl());
            if (decoded.length == 0)
                throw new IllegalStateException("Cloudinary delivery is empty");
            System.out.println(
                    "PASS actual Cloudinary upload, retry, immutable content, HTTPS delivery");
        } finally {
            storage.delete(id);
            if (storage.find(id).isPresent())
                throw new IllegalStateException("Exact test image cleanup failed");
            System.out.println("PASS exact test image cleanup");
        }
    }
}
