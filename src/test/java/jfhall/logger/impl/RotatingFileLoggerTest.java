package jfhall.logger.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jfhall.logger.EntrySerializer;
import jfhall.logger.RotationGranularity;
import lombok.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

// This is more of just a skeleton for the tests thrown together to get this up for review, an
// actualy test suite needs to be set up here.
public class RotatingFileLoggerTest {
  private static final String RECORD_PREFIX = "{\"name\": \"";
  private static final String RECORD_SUFFIX = "\"}";
  private static final String FILE_PREFIX = "simple-logger-test";
  private static final String FILE_NAME_TPL = "'test.log-'u'-'M'-'d'-'H'-'m";
  private static final DateTimeFormatter FILE_NAME_FORMATTER =
      DateTimeFormatter.ofPattern(FILE_NAME_TPL).withZone(ZoneOffset.UTC);

  private List<Instant> nows;
  private ScheduledExecutorService mockExecutor;
  private EntrySerializer mockSerializer;
  private Supplier<Instant> mockInstantSupplier;

  private RotatingFileLogger<TestObject> logger;
  private Map<String, List<TestObject>> testObjectsByFile;

  @BeforeEach
  public void setup() {
    nows = new ArrayList(1_000);
    nows.add(Instant.parse("2022-01-01T04:57:53.868486Z"));

    for (int i = 0; i < 1_000; i++) {
      nows.add(nows.get(i).plus(1, ChronoUnit.MINUTES));
    }

    this.testObjectsByFile = new HashMap<>();

    this.mockExecutor = Mockito.mock(ScheduledExecutorService.class);
    this.mockSerializer = Mockito.mock(EntrySerializer.class);
    this.mockInstantSupplier = Mockito.mock(Supplier.class);

    Mockito.when(this.mockInstantSupplier.get()).thenReturn(nows.get(0));

    Mockito.when(this.mockSerializer.serialize(Mockito.any()))
        .thenAnswer(invok -> serialize(invok.getArgumentAt(0, TestObject.class)));

    this.logger =
        new RotatingFileLogger<TestObject>(
            this.mockExecutor,
            this.mockSerializer,
            FILE_PREFIX,
            FILE_NAME_TPL,
            RotationGranularity.MINUTE,
            this.mockInstantSupplier,
            ZoneOffset.UTC);
  }

  @AfterEach
  public void cleanup() throws IOException {
    final File dir = new File(FILE_PREFIX);
    Arrays.stream(dir.listFiles()).forEach(File::delete);
    dir.delete();
  }

  @Test
  public void testDelayRoundsToNearestTimeUnit() {
    final ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);
    final ArgumentCaptor<Long> periodCaptor = ArgumentCaptor.forClass(Long.class);
    Mockito.verify(this.mockExecutor)
        .scheduleAtFixedRate(
            Mockito.any(),
            delayCaptor.capture(),
            periodCaptor.capture(),
            Mockito.eq(TimeUnit.SECONDS));

    Assertions.assertEquals(7, delayCaptor.getValue());
    Assertions.assertEquals(
        RotationGranularity.MINUTE.getAmountOfSeconds(), periodCaptor.getValue());
  }

  @Test
  public void testFileGetsCreatedDuringRollover() {
    for (int i = 0; i < 1_000; i++) {
      this.logger.write(createTestObject(0, "entry_0_" + i));
    }

    Mockito.when(this.mockInstantSupplier.get()).thenReturn(nows.get(1));

    this.logger.rotate();

    for (int i = 0; i < 1_000; i++) {
      this.logger.write(createTestObject(1, "entry_1_" + i));
    }

    Mockito.when(this.mockInstantSupplier.get()).thenReturn(nows.get(2));

    this.logger.rotate();

    for (int i = 0; i < 1_000; i++) {
      this.logger.write(createTestObject(2, "entry_2_" + i));
    }

    validateTestObjects();
  }

  private void validateTestObjects() {
    Assertions.assertFalse(this.testObjectsByFile.isEmpty(), "No test data written to verify.");

    try {
      for (final Map.Entry<String, List<TestObject>> entry : this.testObjectsByFile.entrySet()) {
        final List<TestObject> fileData =
            new BufferedReader(new FileReader(entry.getKey()))
                .lines()
                .map(this::deserialize)
                .collect(Collectors.toList());

        Assertions.assertEquals(entry.getValue().size(), fileData.size());

        for (int i = 0; i < fileData.size(); i++) {
          Assertions.assertEquals(entry.getValue().get(i), fileData.get(i));
        }
      }
    } catch (final FileNotFoundException e) {
      throw new RuntimeException("Test file not found.", e);
    }
  }

  private TestObject createTestObject(final int index, final String name) {
    final TestObject obj = new TestObject(name);

    final List<TestObject> objs =
        this.testObjectsByFile.computeIfAbsent(getCurrentFilePath(index), __ -> new ArrayList<>());

    objs.add(obj);

    return obj;
  }

  private String getCurrentFilePath(final int index) {
    return FileSystems.getDefault()
        .getPath(FILE_PREFIX, FILE_NAME_FORMATTER.format(nows.get(index)))
        .toString();
  }

  private String serialize(final TestObject object) {
    return RECORD_PREFIX + object.name + RECORD_SUFFIX;
  }

  private TestObject deserialize(final String testObjectString) {
    Assertions.assertTrue(
        testObjectString.startsWith(RECORD_PREFIX),
        "Invalid testObjectString: " + testObjectString);
    Assertions.assertTrue(
        testObjectString.endsWith(RECORD_SUFFIX), "Invalid testObjectString: " + testObjectString);

    return new TestObject(
        testObjectString.substring(
            RECORD_PREFIX.length(), testObjectString.length() - RECORD_SUFFIX.length()));
  }

  @Value
  private static class TestObject {
    private final String name;

    public TestObject(final String name) {
      this.name = name;
    }
  }
}
