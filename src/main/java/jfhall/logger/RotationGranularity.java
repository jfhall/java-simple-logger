package jfhall.logger;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Valid intervals to rotate a log file at when using the RotatingFileLogger. */
@AllArgsConstructor
@Getter
public enum RotationGranularity {
  MINUTE(ChronoUnit.MINUTES, 60),
  HOUR(ChronoUnit.HOURS, 60 * 60),
  DAY(ChronoUnit.DAYS, 24 * 60 * 60);

  private TemporalUnit temporalUnit;
  private long amountOfSeconds;
}
