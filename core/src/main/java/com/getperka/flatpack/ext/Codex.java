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
package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackTypes.erase;
import static com.getperka.flatpack.util.FlatPackTypes.getSingleParameterization;

import com.getperka.flatpack.FlatPackVisitor;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;

/**
 * Implements type-specific encoding and decoding mechanisms.
 * 
 * @param <T> the type of data that the instance operates on
 */
public abstract class Codex<T> implements Walker<T> {
  /**
   * Memoizing this value for the path-tracking saves a non-trivial amount of wall-time.
   */
  private final String simpleName;
  private final Class<T> parameterization;

  protected Codex() {
    simpleName = getClass().getSimpleName();
    parameterization = erase(getSingleParameterization(getClass(), Codex.class));
  }

  /**
   * Visit a non-null value using the supplied visitor.
   * 
   * @param visitor the visitor that is traversing the object graph
   * @param value the value being traversed
   * @param context allows mutation of the object graph
   */
  public abstract void acceptNotNull(FlatPackVisitor visitor, T value, VisitorContext<T> context);

  /**
   * Returns {@code value} if it conforms to the {@code T} type parameter. Due to erasure, this
   * method can only partially verify objects of a parameterized type.
   */
  public T cast(Object value) {
    return parameterization.cast(value);
  }

  /**
   * Returns a type descriptor for the JSON structure created by the Codex implementation.
   */
  public abstract Type describe();

  /**
   * Returns a suffix to append to properties of the Codex's type.
   */
  public String getPropertySuffix() {
    return "";
  }

  /**
   * Returns {@code true} if the value is the default value for an uninitialized instance of a
   * property with that value.
   */
  public boolean isDefaultValue(T value) {
    return value == null;
  }

  /**
   * Reify the given {@link JsonElement} into a Java value. If {@code} element is {@null} or
   * represents a Json {@code null} value, this method will return {@code null}, otherwise this
   * method will delegate to {@link #readNotNull(JsonElement, DeserializationContext)}.
   * 
   * @param element the element that contains the value to reify
   * @param context contextual information for the deserialization process
   * @return the requested value
   */
  public T read(JsonElement element, DeserializationContext context) {
    if (element == null || element.isJsonNull()) {
      return null;
    }
    context.pushPath("(" + simpleName + ".read())");
    try {
      return readNotNull(element, context);
    } catch (Exception e) {
      context.fail(e);
      return null;
    } finally {
      context.popPath();
    }
  }

  /**
   * Reify the given {@link JsonElement} into a Java value.
   * 
   * @param element the element that contains the value to reify
   * @param context contextual information for the deserialization process
   * @return the requested value
   * @throws Exception implementations of this method may throw arbitrary exceptions which will be
   *           reported by {@link #read(JsonElement, DeserializationContext)}
   */
  public abstract T readNotNull(JsonElement element, DeserializationContext context)
      throws Exception;

  /**
   * Visit a value using the supplied visitor.
   * <p>
   * The default implementation delegates to {@link #acceptNotNull} or calls
   * {@link FlatPackVisitor#visitValue visitValue()} / {@link FlatPackVisitor#endVisitValue
   * endVisitValue()} if {@code value} is {@code null}.
   * 
   * @param visitor the visitor that is traversing the object graph
   * @param value the value being traversed
   * @param context allows mutation of the object graph
   */
  @Override
  public void walk(FlatPackVisitor visitor, T value, VisitorContext<T> context) {
    if (value == null) {
      visitor.visitValue(null, this, context);
      visitor.endVisitValue(null, this, context);
    } else {
      acceptNotNull(visitor, value, context);
    }
  }

  /**
   * Write a value into the serialization context. If object is {@code null}, writes a null into
   * {@link SerializationContext#getWriter()}, otherwise delegates to
   * {@link #writeNotNull(Object, SerializationContext)}.
   * 
   * @param object a value to write into {@link SerializationContext#getWriter()}
   * @param context the serialization context
   */
  public void write(T object, SerializationContext context) {
    context.pushPath("(" + simpleName + ".write())");
    try {
      JsonWriter writer = context.getWriter();
      if (object == null) {
        writer.nullValue();
      } else {
        writeNotNull(object, context);
      }
    } catch (Exception e) {
      context.fail(e);
    } finally {
      context.popPath();
    }
  }

  /**
   * Write a value into the serialization context.
   * 
   * @param object a value to write into {@link SerializationContext#getWriter()}
   * @param context the serialization context
   * @throws Exception implementations of this method may throw arbitrary exceptions which will be
   *           reported by {@link #write(Object, SerializationContext)}
   */
  public abstract void writeNotNull(T object, SerializationContext context) throws Exception;
}
