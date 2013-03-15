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

import com.getperka.flatpack.FlatPackVisitor;

/**
 * Acceptors encapsulate a {@link Walker} that is ready to receive an object to traverse.
 * 
 * @param <T> the type of data to traverse, typically an aggregate of some sort
 */
public interface Acceptor<T> {
  /**
   * Visit a value with a {@link FlatPackVisitor}.
   * 
   * @param visitor the visitor to apply the value to
   * @param value the value, which may be {@code null}
   * @return {@code value} or its replacement, which may be {@code null}
   */
  T accept(FlatPackVisitor visitor, T value);
}