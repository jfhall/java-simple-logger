package jfhall.logger;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

/** Valid intervals to rotate a log file at when using the RotatingFileLogger. */
public enum RotationGranularity {
  MINUTE(ChronoUnit.MINUTES),
  HOUR(ChronoUnit.HOURS),
  DAY(ChronoUnit.DAYS);

  private TemporalUnit temporalUnit;

  /** @param temporalUnit The associated {@link TemporalUnit}. */
  RotationGranularity(final TemporalUnit temporalUnit) {
    this.temporalUnit = temporalUnit;
  }

  /** @return The associated {@link TemporalUnit}. */
  public TemporalUnit getTemporalUnit() {
    return this.temporalUnit;
  }
}
