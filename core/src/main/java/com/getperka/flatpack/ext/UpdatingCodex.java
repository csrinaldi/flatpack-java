package com.getperka.flatpack.ext;

/*
 * #%L
 * FlatPack serialization code
 * %%
 * Copyright (C) 2012 - 2013 Perka Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

/**
 * A base type for Codex implementations that intend to update graph values in-place.
 * 
 * @param <T> the type of data to be operated upon.
 */
public abstract class UpdatingCodex<T> extends Codex<T> {

  /**
   * Given a pre-existing value in the object graph being reified and a replament value, returns the
   * value that should be stored in the object graph.
   * <p>
   * If both {@code existingValue} and {@code newValue} are non-null, delegates to
   * {@link #replacementValueNotNull}, otherwise returns {@code newValue}.
   * 
   * @param existingValue the value that was already present in the entity being mutated
   * @param newValue the value contained in the payload
   * @return the value that should be set in the entity
   */
  public T replacementValue(T existingValue, T newValue) {
    if (existingValue != null && newValue != null) {
      return replacementValueNotNull(existingValue, newValue);
    }
    return newValue;
  }

  /**
   * Called by {@link #replacementValue} only if both {@code oldValue} and {@code newValue} are
   * non-null.
   */
  public abstract T replacementValueNotNull(T oldValue, T newValue);
}
