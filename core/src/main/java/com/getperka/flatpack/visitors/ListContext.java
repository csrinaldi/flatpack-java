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

import java.util.List;
import java.util.ListIterator;

import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.ext.Walkers.Acceptor;

/**
 * Allows a {@link List} to be traversed via its {@link ListIterator}.
 * 
 * @param <T> the list's element type
 */
public class ListContext<T> extends VisitorContext<T> implements Acceptor<List<T>> {
  private ListIterator<T> iterator;

  protected ListContext() {}

  @Override
  public List<T> accept(FlatPackVisitor visitor, List<T> list) {
    iterator = list.listIterator();
    while (iterator.hasNext()) {
      getWalker().walk(visitor, iterator.next(), this);
    }
    return list;
  }

  @Override
  public boolean canInsert() {
    return true;
  }

  @Override
  public boolean canRemove() {
    return true;
  }

  @Override
  public boolean canReplace() {
    return true;
  }

  @Override
  public void insertAfter(T newValue) {
    ensure(canInsert());
    markInserted();
    iterator.add(newValue);
  }

  @Override
  public void insertBefore(T newValue) {
    ensure(canInsert());
    markInserted();
    boolean didBackUp = false;
    // This check is necessary if the caller removed the last element
    if (iterator.hasPrevious()) {
      didBackUp = true;
      iterator.previous();
    }
    iterator.add(newValue);
    if (didBackUp) {
      iterator.next();
    }
  }

  @Override
  public void remove() {
    if (didRemove()) {
      return;
    }
    ensure(canRemove());
    markRemoved();
    iterator.remove();
  }

  @Override
  public void replace(T newValue) {
    ensure(canReplace());
    markReplaced();
    iterator.set(newValue);
  }
}