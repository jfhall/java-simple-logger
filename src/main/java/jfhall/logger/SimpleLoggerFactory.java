package jfhall.logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import jfhall.logger.impl.RotatingFileLogger;

public class SimpleLoggerFactory {
  /**
   * @param serializer For converting logging entries to a string that can be written to the log.
   * @param filePrefix The directory to put the log files under.
   * @param fileTemplate The template string to use for the name of the file the log data will be
   *     written to. This template should be an absolute path and a valid java formatter string.
   * @param rotationPeriod The granularity to use for rotating the log file. The files will be
   *     rotated at the beginning of the time unit.
   */
  public static <T> SimpleLogger<T> createRotatingFileLogger(
      final EntrySerializer<T> serializer,
      final String filePrefix,
      final String fileTemplate,
      final RotationGranularity rotationGranularity) {
    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdown));

    return new RotatingFileLogger<T>(
        executor, serializer, filePrefix, fileTemplate, rotationGranularity);
  }
}
