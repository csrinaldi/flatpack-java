package com.getperka.flatpack.ext;

/**
 * A base type for Codex implementations that intend to update graph values in-place.
 * 
 * @param <T> the type of data to be operated upon.
 */
public abstract class UpdatingCodex<T> extends Codex<T> {

  public T replacementValue(T oldValue, T newValue) {
    if (oldValue != null && newValue != null) {
      return replacementValueNotNull(oldValue, newValue);
    }
    return newValue;
  }

  /**
   * XXX document me
   * 
   * @param oldValue
   * @param newValue
   * @return
   */
  public abstract T replacementValueNotNull(T oldValue, T newValue);
}
