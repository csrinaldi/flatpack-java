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

import java.util.List;

import javax.inject.Inject;

import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.visitors.ArrayContext;
import com.getperka.flatpack.visitors.ImmutableContext;
import com.getperka.flatpack.visitors.IterableContext;
import com.getperka.flatpack.visitors.ListContext;
import com.getperka.flatpack.visitors.NullableContext;
import com.getperka.flatpack.visitors.SingletonContext;
import com.google.inject.Injector;

public class Walkers {

  /**
   * Acceptors encapsulate a {@link Walker} that is ready to receive an object to traverse.
   * 
   * @param <T> the type of data to traverse, typically an aggregate of some sort
   */
  public interface Acceptor<T> {
    T accept(FlatPackVisitor visitor, T value);
  }

  /**
   * A Walker embeds a traversal strategy for a type of container object. That is, it is aware of
   * the internal structure of a data type and is used to invoke the methods on a visitor.
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

  private Injector injector;

  /**
   * Construct an acceptor to apply a walker to the elements of an array.
   */
  public <E> Acceptor<E[]> walkArray(Walker<E> element) {
    return create(ArrayContext.class, element);
  }

  /**
   * Construct an acceptor to apply a walker to a single immutable entry.
   */
  public <E> Acceptor<E> walkImmutable(Walker<E> element) {
    return create(ImmutableContext.class, element);
  }

  /**
   * Construct an acceptor to apply a walker to the elements of a collection.
   */
  public <E> Acceptor<Iterable<E>> walkIterable(Walker<E> element) {
    return create(IterableContext.class, element);
  }

  /**
   * Construct an acceptor to apply a walker to the elements of a list.
   */
  public <E> Acceptor<List<E>> walkList(Walker<E> element) {
    return create(ListContext.class, element);
  }

  /**
   * Construct an acceptor to apply a walker to a single element that may be nullified.
   */
  public <E> Acceptor<E> walkNullable(Walker<E> element) {
    return create(NullableContext.class, element);
  }

  /**
   * Construct an acceptor to apply a walker to the value of an entity property. This method will
   * select as appropriate {@link Acceptor} based on the property's metadata.
   */
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
    return create(SingletonContext.class, element);
  }

  @Inject
  void inject(Injector injector) {
    this.injector = injector;
  }

  @SuppressWarnings("unchecked")
  private <A extends Acceptor<?>, E> A create(Class<?> clazz, Walker<E> walker) {
    A toReturn = (A) injector.getInstance(clazz);
    VisitorContext<E> visitorContext = (VisitorContext<E>) toReturn;
    visitorContext.setWalker(walker);
    return toReturn;
  }
}