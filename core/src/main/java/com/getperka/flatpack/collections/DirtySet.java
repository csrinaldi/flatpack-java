/*
 * #%L
 * FlatPack serialization code
 * %%
 * Copyright (C) 2012 Perka Inc.
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
package com.getperka.flatpack.collections;

import java.util.Set;

/**
 * A delegating {@link Set} implementation that will call {@link DirtyFlag#set()} when it is
 * mutated.
 */
public class DirtySet<T> extends DirtyCollection<T> implements Set<T> {
  public DirtySet(Set<T> delegate, DirtyFlag dirty) {
    super(delegate, dirty);
  }
}
