package jfhall.logger.impl;

import java.io.File;
import java.io.IOException;
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
import java.util.function.Supplier;
import jfhall.logger.EntrySerializer;
import jfhall.logger.RotationGranularity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

// This is more of just a skeleton for the tests thrown together to get this up for review, an
// actualy test suite needs to be set up here.
public class RotatingFileLoggerTest {
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
    // do nothing atm
  }

  private TestObject createTestObject(final int index, final String name) {
    final TestObject obj = new TestObject(name);

    final List<TestObject> objs =
        this.testObjectsByFile.getOrDefault(getCurrentFilePath(index), new ArrayList<>());

    objs.add(obj);

    return obj;
  }

  private String getCurrentFilePath(final int index) {
    // TODO make OS independent
    return FILE_PREFIX + "/" + FILE_NAME_FORMATTER.format(nows.get(index));
  }

  private String serialize(final TestObject object) {
    return "{\"name\": \"" + object.name + "\"}";
  }

  private static class TestObject {
    private final String name;

    public TestObject(final String name) {
      this.name = name;
    }
  }
}
