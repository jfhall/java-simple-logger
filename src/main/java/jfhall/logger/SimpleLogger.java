package jfhall.logger;

/**
 * The core interface for this library, used for logging a specific type of message to a specific
 * destination.
 *
 * @param <T> The type of the object to be logged.
 */
public interface SimpleLogger<T> {
  void write(T input);
}
