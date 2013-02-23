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

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.getperka.flatpack.FlatPackVisitor;

/**
 * Allows mutations to the object graph during traversal.
 * <p>
 * The available mutations are dependent upon the value being visited. For example, an element in a
 * list can be replaced, removed, or have siblings added, whereas insertion doesn't make sense for a
 * simple scalar property. For that reason, the various {@code can} methods should be used to guard
 * calls to the mutators.
 * <p>
 * Various container-specific implementations of {@link VisitorContext} are available for use when
 * writing custom {@link Walker} implementations. Each context implementation has an {@code accept}
 * method which handles traversing and mutating some particular type of container object.
 * 
 * @param <T> the type of data currently being visited
 */
public class VisitorContext<T> {
  public interface Acceptor<T> {
    T accept(FlatPackVisitor visitor, T value);
  }

  /**
   * Allows arrays to be visited.
   * 
   * @param <T> the component type of the array
   */
  public static class ArrayContext<T> extends VisitorContext<T> implements Acceptor<T[]> {
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

  /**
   * Disallows all mutations for a single scalar value.
   * 
   * @param <T> the type of data being visited
   */
  public static class ImmutableContext<T> extends VisitorContext<T> implements Acceptor<T> {
    protected ImmutableContext() {}

    @Override
    public T accept(FlatPackVisitor visitor, T value) {
      getWalker().walk(visitor, value, this);
      return value;
    }
  }

  /**
   * Allows an {@link Iterable} to be traversed via its {@link Iterator}.
   * 
   * @param <T> the iterator's element type
   */
  public static class IterableContext<T> extends VisitorContext<T> implements
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

  /**
   * Allows a {@link List} to be traversed via its {@link ListIterator}.
   * 
   * @param <T> the list's element type
   */
  public static class ListContext<T> extends VisitorContext<T> implements Acceptor<List<T>> {
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

  /**
   * Allows a single value to be replaced or removed.
   * 
   * @param <T> the value type
   */
  public static class NullableContext<T> extends SingletonContext<T> {
    protected NullableContext() {}

    @Override
    public boolean canRemove() {
      return true;
    }

    @Override
    public void remove() {
      ensure(canRemove());
      markRemoved();
    }
  }

  /**
   * Allows a single value to be be replaced.
   * 
   * @param <T> the value type
   */
  public static class SingletonContext<T> extends VisitorContext<T> implements Acceptor<T> {
    private T value;

    protected SingletonContext() {}

    @Override
    public T accept(FlatPackVisitor visitor, T singleton) {
      getWalker().walk(visitor, singleton, this);
      return getValue(singleton);
    }

    @Override
    public boolean canReplace() {
      return true;
    }

    @Override
    public void replace(T newValue) {
      ensure(canReplace());
      markReplaced();
      this.value = newValue;
    }

    /**
     * Visible for testing.
     */
    T getValue(T fallback) {
      return didRemove() ? null : didReplace() ? value : fallback;
    }
  }

  /**
   * A Walker is an object that is aware of the internal structure of a data type and is used to
   * invoke the methods on a visitor.
   * 
   * @param <T> the type of data that the Walker operates on
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

  private boolean inserted;

  private boolean removed;
  private boolean replaced;
  private Walker<T> walker;

  protected VisitorContext() {}

  public boolean canInsert() {
    return false;
  }

  public boolean canRemove() {
    return false;
  }

  public boolean canReplace() {
    return false;
  }

  public boolean didInsert() {
    return inserted;
  }

  public boolean didRemove() {
    return removed;
  }

  public boolean didReplace() {
    return replaced;
  }

  public void insertAfter(T newValue) {
    ensure(false);
  }

  public void insertBefore(T newValue) {
    ensure(false);
  }

  public void remove() {
    ensure(false);
  }

  public void replace(T newValue) {
    ensure(false);
  }

  public <E> Acceptor<E[]> walkArray(Walker<E> element) {
    ArrayContext<E> toReturn = new ArrayContext<E>();
    toReturn.setWalker(element);
    return toReturn;
  }

  public <E> Acceptor<E> walkImmutable(Walker<E> element) {
    ImmutableContext<E> toReturn = new ImmutableContext<E>();
    toReturn.setWalker(element);
    return toReturn;
  }

  public <E> Acceptor<Iterable<E>> walkIterable(Walker<E> element) {
    IterableContext<E> toReturn = new IterableContext<E>();
    toReturn.setWalker(element);
    return toReturn;
  }

  public <E> Acceptor<List<E>> walkList(Walker<E> element) {
    ListContext<E> toReturn = new ListContext<E>();
    toReturn.setWalker(element);
    return toReturn;
  }

  public <E> Acceptor<E> walkNullable(Walker<E> element) {
    NullableContext<E> toReturn = new NullableContext<E>();
    toReturn.setWalker(element);
    return toReturn;
  }

  public <E> Acceptor<E> walkProperty(Property property, Walker<E> element) {
    if (property.getSetter() == null) {
      return walkImmutable(element);
    } else if (property.getSetter().getParameterTypes()[0].isPrimitive()) {
      return walkSingleton(element);
    } else {
      return walkNullable(element);
    }
  }

  public <E> Acceptor<E> walkSingleton(Walker<E> element) {
    SingletonContext<E> toReturn = new SingletonContext<E>();
    toReturn.setWalker(element);
    return toReturn;
  }

  protected void ensure(boolean capability) {
    if (!capability) {
      throw new UnsupportedOperationException();
    }
  }

  protected Walker<T> getWalker() {
    return walker;
  }

  protected void markInserted() {
    inserted = true;
  }

  protected void markRemoved() {
    removed = true;
    replaced = false;
  }

  protected void markReplaced() {
    removed = false;
    replaced = true;
  }

  protected void setWalker(Walker<T> walker) {
    this.walker = walker;
  }
}