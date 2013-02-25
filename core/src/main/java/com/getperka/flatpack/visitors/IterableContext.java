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

import java.util.Iterator;

import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.ext.Acceptor;
import com.getperka.flatpack.ext.VisitorContext;

/**
 * Allows an {@link Iterable} to be traversed via its {@link Iterator}.
 * 
 * @param <T> the iterator's element type
 */
public class IterableContext<T> extends VisitorContext<T> implements
    Acceptor<Iterable<T>> {
  private Iterator<T> iterator;

  protected IterableContext() {}

  @Override
  public Iterable<T> accept(FlatPackVisitor visitor, Iterable<T> iterable)
  {
    iterator = iterable.iterator();
    while (iterator.hasNext()) {
      getWalker().walk(visitor, iterator.next(), this);
    }
    return iterable;
  }

  @Override
  public boolean canRemove() {
    return true;
  }

  @Override
  public void remove() {
    ensure(canRemove());
    markRemoved();
    iterator.remove();
  }
}