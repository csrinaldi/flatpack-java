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
 * A Walker embeds a traversal strategy for a type of container object. That is, it is aware of
 * the internal structure of a data type and is used to invoke the methods on a visitor.
 * 
 * @param <T> the type of data that the Walker operates on
 * @see Walkers
 */
public interface Walker<T> {
  /**
   * Invoke the various methods on a visitor to inform it about {@code value}.
   * 
   * @param visitor the visitor to operate on
   * @param value the value the visitor should be informed of
   * @param context the context in which the value is being visited
   */
  void walk(FlatPackVisitor visitor, T value, VisitorContext<T> context);
}