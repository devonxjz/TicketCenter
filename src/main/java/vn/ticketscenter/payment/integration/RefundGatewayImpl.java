package vn.ticketscenter.payment.integration;

import java.nio.ByteBuffer;
import java.nio.channels.FileLock;
import vn.ticketscenter.fulfillment.refund.dto.RefundTransferRequest;
import vn.ticketscenter.fulfillment.refund.dto.RefundTransferResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** LOCAL/TEST simulator. Receipts survive instances and JVM restarts. No bank is contacted. */
public final class RefundGatewayImpl implements RefundGateway {
    private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();

    private final Path directory;
    private final Status outcome;
    private final boolean timeoutAfterSideEffect;

    public RefundGatewayImpl(Path directory, Status outcome, boolean timeoutAfterSideEffect) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        this.outcome = Objects.requireNonNull(outcome);
        this.timeoutAfterSideEffect = timeoutAfterSideEffect;
    }

    @Override
    public Result submit(UUID attempt, BigDecimal amount) throws Timeout {
        new RefundTransferRequest(attempt, attempt, amount);
        Result result =
                locked(
                        attempt,
                        file -> {
                            String vnd = amount.toBigIntegerExact().toString();
                            if (Files.exists(file)) {
                                List<String> lines = read(file);
                                if (!lines.getFirst().equals(vnd)) {
                                    throw new IllegalArgumentException(
                                            "Attempt amount is immutable");
                                }
                                return new Result(Status.valueOf(lines.get(1)), "SIM-" + attempt);
                            }
                            write(file, vnd, outcome);
                            return new Result(outcome, "SIM-" + attempt);
                        });
        if (timeoutAfterSideEffect) {
            throw new Timeout();
        }
        return result;
    }

    @Override
    public Result query(String ref) throws Timeout {
        if (ref == null || !ref.startsWith("SIM-")) {
            return new Result(Status.UNKNOWN, ref);
        }
        try {
            return queryRefundStatus(ref, UUID.fromString(ref.substring(4))).gatewayResult();
        } catch (IllegalArgumentException invalid) {
            return new Result(Status.UNKNOWN, ref);
        }
    }

    @Override
    public RefundTransferResult queryRefundStatus(String ref, UUID attempt) throws Timeout {
        if (ref != null && !ref.equals("SIM-" + attempt)) {
            return new RefundTransferResult(Status.UNKNOWN, ref);
        }
        return locked(
                attempt,
                file ->
                        Files.exists(file)
                                ? new RefundTransferResult(
                                        Status.valueOf(read(file).get(1)), "SIM-" + attempt)
                                : new RefundTransferResult(Status.UNKNOWN, null));
    }

    /** Explicit simulator reconciliation; finalized receipts cannot change truth. */
    public void resolve(UUID attempt, Status finalStatus) throws Timeout {
        if (finalStatus != Status.SUCCEEDED && finalStatus != Status.FAILED) {
            throw new IllegalArgumentException("Final result required");
        }
        locked(
                attempt,
                file -> {
                    List<String> lines = read(file);
                    Status current = Status.valueOf(lines.get(1));
                    if (current != Status.UNKNOWN
                            && current != Status.PENDING
                            && current != finalStatus) {
                        throw new IllegalArgumentException("Final receipt is immutable");
                    }
                    write(file, lines.getFirst(), finalStatus);
                    return null;
                });
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run(Path receipt) throws IOException;
    }

    private <T> T locked(UUID id, Operation<T> work) throws Timeout {
        Path lock = directory.resolve(id + ".lock");
        synchronized (LOCKS.computeIfAbsent(lock, path -> new Object())) {
            try {
                Files.createDirectories(directory);
                try (FileChannel channel =
                                FileChannel.open(
                                        lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                        FileLock guard = channel.lock()) {
                    return work.run(directory.resolve(id + ".receipt"));
                }
            } catch (IOException failure) {
                throw new Timeout();
            }
        }
    }

    private static List<String> read(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private static void write(Path file, String amount, Status status) throws IOException {
        Path temp = Files.createTempFile(file.getParent(), "refund-", ".tmp");
        try {
            try (FileChannel channel =
                    FileChannel.open(
                            temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = StandardCharsets.UTF_8.encode(amount + "\n" + status.name() + "\n");
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(
                    temp,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
