package fi.digitraffic.tis.vaco.rules.internal;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import fi.digitraffic.tis.vaco.http.model.DownloadResponse;
import fi.digitraffic.tis.vaco.http.model.ImmutableDownloadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StopsAndQuaysCacheServiceTests {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final int ZIP_BOMB_ENTRY_COUNT = 10_001;
    private static final String KOOSTE_ENTRY_NAME = "stops.txt";
    private static final String S3_ENTRY_NAME = "s3-copy.txt";

    private StopsAndQuaysCacheService service;
    private VacoProperties vacoProperties;
    private MutableClock clock;

    @Mock
    private VacoHttpClient vacoHttpClient;
    @Mock
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        vacoProperties = TestObjects.vacoProperties();
        clock = MutableClock.startingAt(FIXED_INSTANT);
        service = new StopsAndQuaysCacheService(vacoProperties, vacoHttpClient, s3Client, clock, new SyncTaskExecutor());
    }

    @Test
    void warmUpOnStartupReturnsImmediatelyAndSubmitsOneTaskToExecutor() {
        List<Runnable> submitted = new ArrayList<>();
        StopsAndQuaysCacheService asyncService =
            new StopsAndQuaysCacheService(vacoProperties, vacoHttpClient, s3Client, clock, submitted::add);

        asyncService.warmUpOnStartup();

        assertThat(submitted.size(), equalTo(1));
        assertThat(asyncService.currentCacheFile().isPresent(), equalTo(false));
        verifyNoInteractions(s3Client, vacoHttpClient);
    }

    @Test
    void warmUpOnStartupDoesNotPropagateUnexpectedExceptionFromExecutedTask() {
        VacoProperties failingProperties = mock(VacoProperties.class);
        given(failingProperties.s3PackagesBucket()).willThrow(new IllegalStateException("boom"));
        StopsAndQuaysCacheService failingService =
            new StopsAndQuaysCacheService(failingProperties, vacoHttpClient, s3Client, clock, new SyncTaskExecutor());

        assertDoesNotThrow(failingService::warmUpOnStartup);

        assertThat(failingService.currentCacheFile().isPresent(), equalTo(false));
    }

    @Test
    void scheduledRefreshSuccessUpdatesCacheAndUploadsToS3() throws IOException {
        stubEagerFetchSuccess();
        stubUploadSuccess();

        service.scheduledRefresh();

        assertThat(service.currentCacheFile().isPresent(), equalTo(true));
        Path cacheFile = service.currentCacheFile().orElseThrow();
        assertThat(Files.size(cacheFile), greaterThan(0L));
        verify(s3Client).uploadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class));
    }

    @Test
    void scheduledRefreshFailedDownloadLeavesCacheUntouched() throws IOException {
        stubEagerFetchSuccess();
        stubUploadSuccess();
        service.scheduledRefresh();
        Path firstCacheFile = service.currentCacheFile().orElseThrow();
        byte[] firstContent = Files.readAllBytes(firstCacheFile);

        given(vacoHttpClient.downloadFile(any(Path.class), anyString()))
            .willReturn(CompletableFuture.completedFuture(ImmutableDownloadResponse.builder().result(DownloadResponse.Result.FAILED_DOWNLOAD).build()));

        service.scheduledRefresh();

        assertThat(service.currentCacheFile().orElseThrow(), equalTo(firstCacheFile));
        assertThat(Files.readAllBytes(service.currentCacheFile().orElseThrow()), equalTo(firstContent));
        verify(s3Client, times(1)).uploadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class));
    }

    @Test
    void scheduledRefreshInvalidZipLeavesCacheUntouched() throws IOException {
        stubEagerFetchSuccess();
        stubUploadSuccess();
        service.scheduledRefresh();
        Path firstCacheFile = service.currentCacheFile().orElseThrow();
        byte[] firstContent = Files.readAllBytes(firstCacheFile);

        given(vacoHttpClient.downloadFile(any(Path.class), anyString())).willAnswer(invocation -> {
            Path target = invocation.getArgument(0);
            writeInvalidZip(target);
            return CompletableFuture.completedFuture(ImmutableDownloadResponse.builder().result(DownloadResponse.Result.OK).body(target).build());
        });

        service.scheduledRefresh();

        assertThat(service.currentCacheFile().orElseThrow(), equalTo(firstCacheFile));
        assertThat(Files.readAllBytes(service.currentCacheFile().orElseThrow()), equalTo(firstContent));
        verify(s3Client, times(1)).uploadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class));
    }

    @Test
    void warmUpOnStartupDownloadsFromS3WhenKeyExists() throws IOException {
        given(s3Client.keyExists(eq(vacoProperties.s3PackagesBucket()), anyString())).willReturn(true);
        stubS3RestoreValidZip(FIXED_INSTANT);

        service.warmUpOnStartup();

        assertThat(service.currentCacheFile().isPresent(), equalTo(true));
        assertThat(Files.size(service.currentCacheFile().orElseThrow()), greaterThan(0L));
        verify(s3Client).downloadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class));
        verify(vacoHttpClient, never()).downloadFile(any(Path.class), anyString());
    }

    @Test
    void warmUpOnStartupKeepsStaleRestoredCopyAsFallbackWhenFetchFails() throws IOException {
        given(s3Client.keyExists(eq(vacoProperties.s3PackagesBucket()), anyString())).willReturn(true);
        Instant lastModified = FIXED_INSTANT.minus(Duration.ofHours(31));
        stubS3RestoreValidZip(lastModified);
        // Interrupting the worker after the failed attempt makes the retry loop abort instead of sleeping between attempts.
        given(vacoHttpClient.downloadFile(any(Path.class), anyString())).willAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return CompletableFuture.completedFuture(ImmutableDownloadResponse.builder().result(DownloadResponse.Result.FAILED_DOWNLOAD).build());
        });

        try {
            service.warmUpOnStartup();
        } finally {
            Thread.interrupted();
        }

        verify(vacoHttpClient, times(1)).downloadFile(any(Path.class), anyString());
        assertThat(entryName(service.currentCacheFile().orElseThrow()), equalTo(S3_ENTRY_NAME));
        assertThat(service.lastSuccessfulRefreshAt().orElseThrow(), equalTo(lastModified));
        assertThat(service.isStale(), equalTo(true));
    }

    @Test
    void warmUpOnStartupFetchesFreshCopyWhenRestoredCopyIsStale() throws IOException {
        given(s3Client.keyExists(eq(vacoProperties.s3PackagesBucket()), anyString())).willReturn(true);
        stubS3RestoreValidZip(FIXED_INSTANT.minus(Duration.ofHours(31)));
        stubEagerFetchSuccess();
        stubUploadSuccess();

        service.warmUpOnStartup();

        verify(vacoHttpClient, times(1)).downloadFile(any(Path.class), anyString());
        assertThat(entryName(service.currentCacheFile().orElseThrow()), equalTo(KOOSTE_ENTRY_NAME));
        assertThat(service.lastSuccessfulRefreshAt().orElseThrow(), equalTo(clock.instant()));
        assertThat(service.isStale(), equalTo(false));
    }

    @Test
    void warmUpOnStartupDoesNotOverwriteCacheRefreshedWhileS3DownloadWasInFlight() throws IOException {
        given(s3Client.keyExists(eq(vacoProperties.s3PackagesBucket()), anyString())).willReturn(true);
        stubEagerFetchSuccess();
        stubUploadSuccess();
        Instant oldLastModified = FIXED_INSTANT.minus(Duration.ofHours(2));
        given(s3Client.downloadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class))).willAnswer(invocation -> {
            Path target = invocation.getArgument(2);
            service.scheduledRefresh();
            writeValidZip(target, S3_ENTRY_NAME);
            return new S3Client.DownloadedFile(Files.size(target), oldLastModified);
        });

        service.warmUpOnStartup();

        verify(vacoHttpClient, times(1)).downloadFile(any(Path.class), anyString());
        assertThat(entryName(service.currentCacheFile().orElseThrow()), equalTo(KOOSTE_ENTRY_NAME));
        assertThat(service.lastSuccessfulRefreshAt().orElseThrow(), equalTo(clock.instant()));
    }

    @Test
    void warmUpOnStartupDoesNotRetryWhenFetchSucceededButS3UploadFailed() {
        given(s3Client.keyExists(eq(vacoProperties.s3PackagesBucket()), anyString())).willReturn(false);
        stubEagerFetchSuccess();
        given(s3Client.uploadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class)))
            .willReturn(CompletableFuture.failedFuture(new IllegalStateException("upload failed")));

        service.warmUpOnStartup();

        verify(vacoHttpClient, times(1)).downloadFile(any(Path.class), anyString());
        assertThat(service.currentCacheFile().isPresent(), equalTo(true));
        assertThat(service.lastSuccessfulRefreshAt().orElseThrow(), equalTo(clock.instant()));
    }

    @Test
    void warmUpOnStartupUsesS3LastModifiedAndIsNotStaleWhenCopyIsRecent() throws IOException {
        given(s3Client.keyExists(eq(vacoProperties.s3PackagesBucket()), anyString())).willReturn(true);
        Instant lastModified = FIXED_INSTANT.minus(Duration.ofHours(2));
        stubS3RestoreValidZip(lastModified);

        service.warmUpOnStartup();

        assertThat(service.lastSuccessfulRefreshAt().orElseThrow(), equalTo(lastModified));
        assertThat(service.isStale(), equalTo(false));
        verify(vacoHttpClient, never()).downloadFile(any(Path.class), anyString());
    }

    @Test
    void warmUpOnStartupFallsThroughToEagerFetchWhenS3CopyFailsValidation() throws IOException {
        given(s3Client.keyExists(eq(vacoProperties.s3PackagesBucket()), anyString())).willReturn(true);
        given(s3Client.downloadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class))).willAnswer(invocation -> {
            Path target = invocation.getArgument(2);
            writeInvalidZip(target);
            return new S3Client.DownloadedFile(Files.size(target), FIXED_INSTANT);
        });
        stubEagerFetchSuccess();
        stubUploadSuccess();

        service.warmUpOnStartup();

        assertThat(service.currentCacheFile().isPresent(), equalTo(true));
        assertThat(Files.size(service.currentCacheFile().orElseThrow()), greaterThan(0L));
        verify(s3Client).downloadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class));
        verify(vacoHttpClient).downloadFile(any(Path.class), anyString());
        verify(s3Client).uploadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class));
    }

    @Test
    void warmUpOnStartupFallsThroughToEagerFetchWhenS3CopyHasNoLastModified() throws IOException {
        given(s3Client.keyExists(eq(vacoProperties.s3PackagesBucket()), anyString())).willReturn(true);
        given(s3Client.downloadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class))).willAnswer(invocation -> {
            Path target = invocation.getArgument(2);
            writeValidZip(target);
            return new S3Client.DownloadedFile(Files.size(target), null);
        });
        stubEagerFetchSuccess();
        stubUploadSuccess();

        service.warmUpOnStartup();

        assertThat(service.currentCacheFile().isPresent(), equalTo(true));
        assertThat(service.lastSuccessfulRefreshAt().orElseThrow(), equalTo(clock.instant()));
        verify(vacoHttpClient).downloadFile(any(Path.class), anyString());
        verify(s3Client).uploadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class));
    }

    @Test
    void warmUpOnStartupEagerFetchesOnColdStart() {
        given(s3Client.keyExists(eq(vacoProperties.s3PackagesBucket()), anyString())).willReturn(false);
        stubEagerFetchSuccess();
        stubUploadSuccess();

        service.warmUpOnStartup();

        assertThat(service.currentCacheFile().isPresent(), equalTo(true));
        verify(vacoHttpClient).downloadFile(any(Path.class), anyString());
        verify(s3Client).uploadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class));
        verify(s3Client, never()).downloadFile(anyString(), any(S3Path.class), any(Path.class));
    }

    @Test
    void scheduledRefreshZipBombExceedingEntryCapLeavesCacheUntouched() throws IOException {
        stubEagerFetchSuccess();
        stubUploadSuccess();
        service.scheduledRefresh();
        Path firstCacheFile = service.currentCacheFile().orElseThrow();
        byte[] firstContent = Files.readAllBytes(firstCacheFile);

        given(vacoHttpClient.downloadFile(any(Path.class), anyString())).willAnswer(invocation -> {
            Path target = invocation.getArgument(0);
            writeZipExceedingEntryLimit(target);
            return CompletableFuture.completedFuture(ImmutableDownloadResponse.builder().result(DownloadResponse.Result.OK).body(target).build());
        });

        service.scheduledRefresh();

        assertThat(service.currentCacheFile().orElseThrow(), equalTo(firstCacheFile));
        assertThat(Files.readAllBytes(service.currentCacheFile().orElseThrow()), equalTo(firstContent));
        verify(s3Client, times(1)).uploadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class));
    }

    @Test
    void overlappingRefreshesBothCompleteAndLeaveValidCacheFile() throws Exception {
        CountDownLatch firstDownloadStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstDownload = new CountDownLatch(1);
        AtomicInteger downloads = new AtomicInteger();
        given(vacoHttpClient.downloadFile(any(Path.class), anyString())).willAnswer(invocation -> {
            Path target = invocation.getArgument(0);
            writeValidZip(target);
            if (downloads.incrementAndGet() == 1) {
                firstDownloadStarted.countDown();
                assertThat(releaseFirstDownload.await(10, TimeUnit.SECONDS), equalTo(true));
            }
            return CompletableFuture.completedFuture(ImmutableDownloadResponse.builder().result(DownloadResponse.Result.OK).body(target).build());
        });
        stubUploadSuccess();

        CompletableFuture<Void> first = CompletableFuture.runAsync(service::scheduledRefresh);
        assertThat(firstDownloadStarted.await(10, TimeUnit.SECONDS), equalTo(true));
        service.scheduledRefresh();
        releaseFirstDownload.countDown();
        first.get(10, TimeUnit.SECONDS);

        verify(vacoHttpClient, times(2)).downloadFile(any(Path.class), anyString());
        verify(s3Client, times(2)).uploadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class));
        assertThat(Files.size(service.currentCacheFile().orElseThrow()), greaterThan(0L));
    }

    @Test
    void lastSuccessfulRefreshAtIsEmptyBeforeAnySuccessfulRefresh() {
        assertThat(service.lastSuccessfulRefreshAt().isPresent(), equalTo(false));
    }

    @Test
    void lastSuccessfulRefreshAtReturnsTimestampAfterSuccessfulRefresh() {
        stubEagerFetchSuccess();
        stubUploadSuccess();

        service.scheduledRefresh();

        assertThat(service.lastSuccessfulRefreshAt().isPresent(), equalTo(true));
        assertThat(service.lastSuccessfulRefreshAt().orElseThrow(), equalTo(clock.instant()));
    }

    @Test
    void isStaleIsFalseWhenNoRefreshHasEverSucceeded() {
        assertThat(service.isStale(), equalTo(false));
    }

    @Test
    void isStaleIsFalseBeforeStalenessThresholdElapses() {
        stubEagerFetchSuccess();
        stubUploadSuccess();
        service.scheduledRefresh();

        clock.advanceBy(Duration.ofHours(29));

        assertThat(service.isStale(), equalTo(false));
    }

    @Test
    void isStaleIsTrueAfterStalenessThresholdElapsesWithoutSuccessfulRefresh() {
        stubEagerFetchSuccess();
        stubUploadSuccess();
        service.scheduledRefresh();

        clock.advanceBy(Duration.ofHours(31));

        assertThat(service.isStale(), equalTo(true));
    }

    private void stubEagerFetchSuccess() {
        given(vacoHttpClient.downloadFile(any(Path.class), anyString())).willAnswer(invocation -> {
            Path target = invocation.getArgument(0);
            writeValidZip(target);
            return CompletableFuture.completedFuture(ImmutableDownloadResponse.builder().result(DownloadResponse.Result.OK).body(target).build());
        });
    }

    private void stubS3RestoreValidZip(Instant lastModified) {
        given(s3Client.downloadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class))).willAnswer(invocation -> {
            Path target = invocation.getArgument(2);
            writeValidZip(target, S3_ENTRY_NAME);
            return new S3Client.DownloadedFile(Files.size(target), lastModified);
        });
    }

    private void stubUploadSuccess() {
        given(s3Client.uploadFile(eq(vacoProperties.s3PackagesBucket()), any(S3Path.class), any(Path.class)))
            .willReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));
    }

    private static void writeValidZip(Path target) throws IOException {
        writeValidZip(target, KOOSTE_ENTRY_NAME);
    }

    private static String entryName(Path zip) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            return zis.getNextEntry().getName();
        }
    }

    private static void writeValidZip(Path target, String entryName) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write("stop_id,stop_name\n1,Test Stop".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private static void writeInvalidZip(Path target) throws IOException {
        Files.write(target, new byte[0]);
    }

    private static void writeZipExceedingEntryLimit(Path target) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            for (int i = 0; i < ZIP_BOMB_ENTRY_COUNT; i++) {
                zos.putNextEntry(new ZipEntry("entry-" + i));
                zos.closeEntry();
            }
        }
    }

    /** Test-only {@link Clock} whose {@link #instant()} can be advanced without reconstructing the service under test. */
    private static final class MutableClock extends Clock {
        private final ZoneId zone;
        private Instant instant;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        static MutableClock startingAt(Instant instant) {
            return new MutableClock(instant, ZoneId.of("UTC"));
        }

        void advanceBy(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
