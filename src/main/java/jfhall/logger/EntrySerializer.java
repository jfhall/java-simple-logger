package jfhall.logger;

/**
 * An abstraction for the mechanism used to serialize every event of type T to a String that can be
 * written to the destination.
 *
 * @param <T> The type of object that will be serialized.
 */
public interface EntrySerializer<T> {
  String serialize(T entry);
}
