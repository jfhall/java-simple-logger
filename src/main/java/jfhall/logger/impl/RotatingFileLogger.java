package jfhall.logger.impl;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import jfhall.logger.EntrySerializer;
import jfhall.logger.RotationGranularity;
import jfhall.logger.SimpleLogger;

/** A SimpleLogger that will write to a physical file, rotating to a new file at a set interval. */
public class RotatingFileLogger<T> implements SimpleLogger<T> {
  // OutputStream is chosen for simplicity, but something like AsynchronousFileChannel may be a
  // better approach to avoid impacting the publishing thread.
  private volatile OutputStream output;

  private final String filePrefix;
  private final DateTimeFormatter fileNameFormatter;
  private final RotationGranularity rotationGranularity;
  private final EntrySerializer<T> serializer;
  private final ScheduledExecutorService executor;
  private final Supplier<Instant> instantSupplier;

  /**
   * @param executor The executor service to schedule the file rotation job on.
   * @param serializer Used for serializing the input object into a string that can be written to
   *     the log file. time variables. The template needs to be a valid DateTimeFormatter format
   *     string.
   * @param filePrefix The directory to put the log files under.
   * @param fileTemplate The string template to get the file name from after replacing the time
   *     variables. The template needs to be a valid DateTimeFormatter format string.
   * @param rotationGranularity How often to rotate the log files.
   */
  public RotatingFileLogger(
      final ScheduledExecutorService executor,
      final EntrySerializer<T> serializer,
      final String filePrefix,
      final String fileTemplate,
      final RotationGranularity rotationGranularity) {
    this(
        executor,
        serializer,
        filePrefix,
        fileTemplate,
        rotationGranularity,
        Instant::now,
        ZoneOffset.UTC);
  }

  /**
   * @param executor The executor service to schedule the file rotation job on.
   * @param serializer Used for serializing the input object into a string that can be written to
   *     the log file.
   * @param filePrefix The directory to put the log files under.
   * @param fileTemplate The string template to get the file name from after replacing the time
   *     variables. The template needs to be a valid DateTimeFormatter format string.
   * @param rotationGranularity How often to rotate the log files.
   * @param zoneId The ZoneId to use when generating the file names.
   */
  public RotatingFileLogger(
      final ScheduledExecutorService executor,
      final EntrySerializer<T> serializer,
      final String filePrefix,
      final String fileTemplate,
      final RotationGranularity rotationGranularity,
      final ZoneId zoneId) {
    this(executor, serializer, filePrefix, fileTemplate, rotationGranularity, Instant::now, zoneId);
  }

  /**
   * VisibleForTesting.
   *
   * @param executor The executor service to schedule the file rotation job on.
   * @param serializer Used for serializing the input object into a string that can be written to
   *     the log file.
   * @param filePrefix The directory to put the log files under.
   * @param fileTemplate The string template to get the file name from after replacing the time
   *     variables. The template needs to be a valid DateTimeFormatter format string.
   * @param rotationGranularity How often to rotate the log files.
   * @param instantSupplier Get an Instant for now.
   * @param zoneId The ZoneId to use when generating the file names.
   */
  RotatingFileLogger(
      final ScheduledExecutorService executor,
      final EntrySerializer<T> serializer,
      final String filePrefix,
      final String fileTemplate,
      final RotationGranularity rotationGranularity,
      final Supplier<Instant> instantSupplier,
      final ZoneId zoneId) {
    this.filePrefix = filePrefix;
    this.fileNameFormatter = DateTimeFormatter.ofPattern(fileTemplate).withZone(zoneId);

    this.rotationGranularity = rotationGranularity;
    this.serializer = serializer;
    this.executor = executor;
    this.instantSupplier = instantSupplier;

    final File parentDir = new File(filePrefix);

    // Create the parent directories for the log file.
    if (!parentDir.mkdirs() && !parentDir.exists()) {
      // TODO make a better exception
      throw new RuntimeException("Could not create parent log directories.");
    }

    final Instant now = instantSupplier.get();

    // Set up a job to rotate the log file.
    this.executor.scheduleAtFixedRate(
        this::rotate, calculateInitialDelay(now), 60_000, TimeUnit.MILLISECONDS);

    initializeOutput(now);
  }

  /** {@inheritDoc}. */
  @Override
  public void write(final T input) {
    try {
      final String content = String.format("%s%n", this.serializer.serialize(input));
      this.output.write(content.getBytes(StandardCharsets.UTF_8));
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to close simple logger file channel.", e);
    }
  }

  /**
   * Rotate the output stream, setting up the new destination and closing the old one.
   *
   * <p>VisibleForTesting
   */
  void rotate() {
    final OutputStream oldOutput = this.output;

    initializeOutput(instantSupplier.get());

    try {
      oldOutput.close();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to close simple logger file channel.", e);
    }
  }

  /** Initialize the output stream resources. */
  private void initializeOutput(final Instant now) {
    try {
      // TODO this buffer size needs to be tuned.
      this.output =
          new BufferedOutputStream(
              new FileOutputStream(new File(this.filePrefix, getFileName(now))));
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create simple logger file channel.", e);
    }
  }

  /**
   * Calculate the milliseconds to delay the initial rollover operation.
   *
   * @param now The current moment, the delay will be the time difference between this and the
   *     beginning of the next "rotationGranularity moment" (e.g. the beginning of the next minute).
   * @return The delay in milliseconds before the first rollover operation should be performed.
   */
  private long calculateInitialDelay(final Instant now) {
    return now.plus(1, this.rotationGranularity.getTemporalUnit())
            .truncatedTo(this.rotationGranularity.getTemporalUnit())
            .toEpochMilli()
        - now.toEpochMilli();
  }

  /**
   * Get the absolute file path from the template, using the provided Instant object to fill in the
   * variables.
   *
   * @param now The timestamp to use for the file template.
   * @return The absolute file path.
   */
  private String getFileName(final Instant now) {
    return this.fileNameFormatter.format(now);
  }
}
