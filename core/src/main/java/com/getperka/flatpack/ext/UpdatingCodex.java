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
