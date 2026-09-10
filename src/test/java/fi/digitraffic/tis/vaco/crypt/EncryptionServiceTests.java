package fi.digitraffic.tis.vaco.crypt;

import tools.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.TestObjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsAsyncClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class EncryptionServiceTests {

    @Mock
    private KmsAsyncClient kmsAsyncClient;

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(kmsAsyncClient);
    }

    @Test
    void name() {
        EncryptionService service = new EncryptionService(TestObjects.vacoProperties(), new ObjectMapper(), kmsAsyncClient);
        String original = "Hail, traveler!";

        assertThat("Roundtripping simple string works",
            service.decrypt(service.encrypt(original), String.class),
            equalTo(original));

        Testing t = new Testing("yo yo yo");
        assertThat("Roundtripping complex type works",
            service.decrypt(service.encrypt(t), Testing.class),
            equalTo(t));
    }

    /**
     * A shared, non-thread-safe {@code Cipher} instance let concurrent encrypt()/decrypt() calls
     * race on init()/doFinal(), corrupting results or throwing.
     */
    @Test
    @Timeout(30)
    void concurrentEncryptDecryptDoesNotCorruptResults() throws Exception {
        // given: many tasks racing to encrypt/decrypt their own distinct input at the same instant
        EncryptionService service = new EncryptionService(TestObjects.vacoProperties(), new ObjectMapper(), kmsAsyncClient);
        int threadCount = 32;
        int totalTasks = 320;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Callable<TaskOutcome>> tasks = new ArrayList<>();
        for (int i = 0; i < totalTasks; i++) {
            String input = "concurrent-task-" + i + "-secret-payload";
            tasks.add(() -> {
                try {
                    barrier.await();
                    String cypher = service.encrypt(input);
                    String decrypted = service.decrypt(cypher, String.class);
                    return new TaskOutcome(input, decrypted, null);
                } catch (Exception e) {
                    return new TaskOutcome(input, null, e);
                }
            });
        }

        // when: all tasks run concurrently, synchronized via CyclicBarrier to force interleaving
        List<Future<TaskOutcome>> futures = executor.invokeAll(tasks, 20, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // then: every task must succeed and decrypt back to exactly what it encrypted
        List<String> failures = new ArrayList<>();
        for (Future<TaskOutcome> future : futures) {
            TaskOutcome outcome = future.get();
            if (outcome.exception() != null) {
                failures.add("input '" + outcome.input() + "' threw " + outcome.exception());
            } else if (!outcome.input().equals(outcome.decrypted())) {
                failures.add("input '" + outcome.input() + "' decrypted to '" + outcome.decrypted() + "' instead of itself");
            }
        }

        assertThat("No concurrent encrypt/decrypt task should throw or return a corrupted/mismatched result, but found: " + failures,
            failures, empty());
    }

    private record TaskOutcome(String input, String decrypted, Exception exception) {}

    public record Testing(String greeting) {}
}
