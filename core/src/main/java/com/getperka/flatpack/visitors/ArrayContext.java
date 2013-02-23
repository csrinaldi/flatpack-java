package com.getperka.flatpack.visitors;

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
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.ext.Walkers.Acceptor;

/**
 * Allows arrays to be visited.
 * 
 * @param <T> the component type of the array
 */
public class ArrayContext<T> extends VisitorContext<T> implements Acceptor<T[]> {
  private T[] array;
  private int index;

  protected ArrayContext() {}

  @Override
  public T[] accept(FlatPackVisitor visitor, T[] array)
  {
    this.array = array;
    index = 0;
    for (int j = array.length; index < j; index++) {
      getWalker().walk(visitor, array[index], this);
    }
    return array;
  }

  @Override
  public boolean canReplace() {
    return true;
  }

  @Override
  public void replace(T newValue) {
    ensure(canReplace());
    markReplaced();
    array[index] = newValue;
  }
}