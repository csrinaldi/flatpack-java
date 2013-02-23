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
import com.getperka.flatpack.HasUuid;

/**
 * Allows mutations to the object graph during traversal.
 * <p>
 * The available mutations are dependent upon the value being visited. For example, an element in a
 * list can be replaced, removed, or have siblings added, whereas insertion doesn't make sense for a
 * simple scalar property. For that reason, the various {@code can} methods should be used to guard
 * calls to the mutators.
 * <p>
 * Various container-specific implementations of {@link VisitorContext} are available for use when
 * writing custom {@link Codex} implementations. Each context implementation has an {@code accept}
 * method which handles traversing and mutating some particular type of container object.
 * 
 * @param <T> the type of data currently being visited
 */
public abstract class VisitorContext<T> {
  /**
   * Allows arrays to be visited.
   * 
   * @param <T> the component type of the array
   */
  public static class ArrayContext<T> extends VisitorContext<T> {
    private T[] array;
    private int index;

    public void acceptArray(FlatPackVisitor visitor, T[] array, Codex<T> codex)
    {
      this.array = array;
      index = 0;
      for (int j = array.length; index < j; index++) {
        codex.accept(visitor, array[index], this);
      }
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
  public static class ImmutableContext<T> extends VisitorContext<T> {
    public void acceptImmutable(FlatPackVisitor visitor, T value, Codex<T> codex) {
      codex.accept(visitor, value, this);
    }
  }

  /**
   * Allows an {@link Iterable} to be traversed via its {@link Iterator}.
   * 
   * @param <T> the iterator's element type
   */
  public static class IterableContext<T> extends VisitorContext<T> {
    private Iterator<T> iterator;

    public void acceptIterable(FlatPackVisitor visitor, Iterable<T> iterable, Codex<T> codex)
    {
      iterator = iterable.iterator();
      while (iterator.hasNext()) {
        codex.accept(visitor, iterator.next(), this);
      }
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
  public static class ListContext<T> extends VisitorContext<T> {
    private ListIterator<T> iterator;

    public void acceptList(FlatPackVisitor visitor, List<T> list, Codex<T> codex) {
      iterator = list.listIterator();
      while (iterator.hasNext()) {
        codex.accept(visitor, iterator.next(), this);
      }
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
    @Override
    public boolean canRemove() {
      return true;
    }

    @Override
    public T getValue() {
      return didRemove() ? null : super.getValue();
    }

    @Override
    public void remove() {
      ensure(canRemove());
      markRemoved();
    }
  }

  /**
   * Allows a single property to be traversed.
   * <p>
   * If the Property is read-only, mutations will be disabled. Otherwise, reference values may be
   * replaced or removed. Primitive values may only ever be replaced.
   * <p>
   * If the context is mutated, the new value will be available from {@link #getValue()}; this
   * context class does not mutate the underlying property.
   * 
   * @param <P> the type of data stored in the property
   */
  public static class PropertyContext<P> extends NullableContext<P> {
    private Property property;

    public void acceptProperty(FlatPackVisitor visitor, HasUuid entity, Property property, P value) {
      this.property = property;

      @SuppressWarnings("unchecked")
      Codex<P> codex = (Codex<P>) property.getCodex();
      codex.accept(visitor, value, this);
    }

    /**
     * Allows removal if the property has a setter and is not of a primitive type.
     */
    @Override
    public boolean canRemove() {
      return canReplace() && !property.getSetter().getParameterTypes()[0].isPrimitive();
    }

    /**
     * Allows replacement if the property has a setter.
     */
    @Override
    public boolean canReplace() {
      return property.getSetter() != null;
    }
  }

  /**
   * Allows a single value to be be replaced.
   * 
   * @param <T> the value type
   */
  public static class SingletonContext<T> extends VisitorContext<T> {
    private T value;

    public void acceptSingleton(FlatPackVisitor visitor, T singleton, Codex<T> codex) {
      codex.accept(visitor, singleton, this);
    }

    @Override
    public boolean canReplace() {
      return true;
    }

    public T getValue() {
      return value;
    }

    @Override
    public void replace(T newValue) {
      ensure(canReplace());
      markReplaced();
      this.value = newValue;
    }
  }

  private boolean inserted;
  private boolean removed;
  private boolean replaced;

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

  protected void ensure(boolean capability) {
    if (!capability) {
      throw new UnsupportedOperationException();
    }
  }

  protected void markInserted() {
    inserted = true;
  }

  protected void markRemoved() {
    removed = true;
  }

  protected void markReplaced() {
    replaced = true;
  }
}