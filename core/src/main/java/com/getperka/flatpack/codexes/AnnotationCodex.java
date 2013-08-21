package com.getperka.flatpack.codexes;

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

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;
import static com.getperka.flatpack.util.FlatPackCollections.sortedMapForIteration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.getperka.flatpack.ext.DeserializationContext;
import com.getperka.flatpack.ext.JsonKind;
import com.getperka.flatpack.ext.SerializationContext;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.ext.TypeHint;
import com.getperka.flatpack.inject.FlatPackLogger;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

/**
 * Encodes Java annotations as simple datastructures. If an annotation being deserialized is not
 * available in the classpath, it will be replaced with an {@link UnknownAnnotation}. The annotation
 * instances produced by this codex will also implement the {@link AnnotationInfo} interface, which
 * allows map-based access to annotation properties.
 */
public class AnnotationCodex extends ValueCodex<Annotation> {
  static class Handler implements InvocationHandler, AnnotationInfo {
    private final Class<? extends Annotation> annotationType;
    private final String annotationTypeName;
    private final Map<String, Object> values;

    Handler(Class<? extends Annotation> annotationType, Map<String, Object> values) {
      this.annotationType = annotationType;
      this.annotationTypeName = annotationType.getName();
      this.values = values;
    }

    Handler(String annotationTypeName, Map<String, Object> values) {
      this.annotationType = UnknownAnnotation.class;
      this.annotationTypeName = annotationTypeName;
      this.values = values;
    }

    @Override
    public String getAnnotationTypeName() {
      return annotationTypeName;
    }

    @Override
    public Map<String, Object> getAnnotationValues() {
      return values;
    }

    @Override
    public int hashCode() {
      return values.hashCode();
    }

    @Override
    public Object invoke(Object instance, Method m, Object[] args) throws Throwable {
      if (Object.class.equals(m.getDeclaringClass())) {
        if (m.getName().equals("equals")) {
          return equals((Annotation) instance, args[0]);
        }
        return m.invoke(this, args);
      }
      if (AnnotationInfo.class.equals(m.getDeclaringClass())) {
        return m.invoke(this, args);
      }
      if (m.getName().equals("annotationType")) {
        return annotationType;
      }
      Object toReturn = values.get(m.getName());
      if (toReturn == null) {
        return m.getDefaultValue();
      }
      return toReturn;
    }

    @Override
    public String toString() {
      return values.toString();
    }

    private List<?> asList(Object array) {
      List<Object> toReturn = listForAny();
      for (int i = 0, j = Array.getLength(array); i < j; i++) {
        toReturn.add(Array.get(array, i));
      }
      return toReturn;
    }

    private boolean equals(Annotation instance, Object obj) {
      // Ensure the incoming object is an annotation of the same type
      if (!(obj instanceof Annotation)
        || !annotationType.equals(((Annotation) obj).annotationType())) {
        return false;
      }

      if (obj instanceof AnnotationInfo) {
        AnnotationInfo info = (AnnotationInfo) obj;
        if (!annotationTypeName.equals(info.getAnnotationTypeName())) {
          return false;
        }
      }

      // Quick test for comparison to self
      if (Proxy.isProxyClass(obj.getClass()) && Proxy.getInvocationHandler(obj) instanceof Handler) {
        Handler handler = (Handler) Proxy.getInvocationHandler(obj);
        if (this == handler) {
          return true;
        }
      }
      return extractValues(instance).equals(extractValues((Annotation) obj));
    }

    private Map<String, Object> extractValues(Annotation obj) {
      Map<String, Object> compareTo = sortedMapForIteration();

      // Support for UnknownAnnotation
      if (obj instanceof AnnotationInfo) {
        AnnotationInfo info = (AnnotationInfo) obj;
        for (Map.Entry<String, Object> entry : info.getAnnotationValues().entrySet()) {
          Object value = entry.getValue();
          if (value.getClass().isArray()) {
            value = asList(value);
          }
          compareTo.put(entry.getKey(), value);
          System.out.println(entry.getKey() + " " + value.getClass().getName() + " " + value);
        }
        return compareTo;
      }

      for (Method m : annotationType.getDeclaredMethods()) {
        m.setAccessible(true);
        Throwable ex;
        try {
          Object value = m.invoke(obj);
          if (m.getReturnType().isArray()) {
            value = asList(value);
          }
          compareTo.put(m.getName(), value);
          continue;
        } catch (IllegalAccessException e) {
          // Unexpected, since interface methods are public
          ex = e;
        } catch (InvocationTargetException e) {
          ex = e.getCause();
        }
        throw new RuntimeException("Could not extract annotation value", ex);
      }
      return compareTo;
    }
  }

  private static final String TYPE_KEY = "@";

  private static final Type TYPE = new Type.Builder()
      .withJsonKind(JsonKind.ANY)
      .withTypeHint(TypeHint.create(Annotation.class))
      .build();

  @Inject
  private DynamicCodex dynamicCodex;
  @FlatPackLogger
  @Inject
  private Logger logger;
  @Inject
  private TypeContext typeContext;

  /**
   * Requires injection.
   */
  protected AnnotationCodex() {}

  @Override
  public Type describe() {
    return TYPE;
  }

  @Override
  public Annotation readNotNull(JsonElement element, DeserializationContext context)
      throws Exception {
    JsonObject obj = element.getAsJsonObject();
    if (!obj.has(TYPE_KEY)) {
      logger.error("Incoming annotation has no @ member");
      return null;
    }

    String typeName = obj.get(TYPE_KEY).getAsString();
    Map<String, Object> values = sortedMapForIteration();

    Class<? extends Annotation> annotationType;
    Handler h;
    try {
      annotationType =
          Class.forName(typeName, false, Thread.currentThread().getContextClassLoader())
              .asSubclass(Annotation.class);

      for (Method m : annotationType.getDeclaredMethods()) {
        JsonElement elt = obj.get(m.getName());
        if (elt == null || elt.isJsonNull()) {
          continue;
        }
        Object value = typeContext.getCodex(m.getGenericReturnType()).read(elt, context);
        values.put(m.getName(), value);
      }
      h = new Handler(annotationType, Collections.unmodifiableMap(values));
    } catch (ClassCastException e) {
      logger.warn(
          "Attempting to decode an annotation type @{} which is not assignable to Annotation",
          typeName);
      return null;
    } catch (ClassNotFoundException e) {
      annotationType = UnknownAnnotation.class;

      for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
        if (TYPE_KEY.equals(entry.getKey())) {
          continue;
        }
        Object value;
        if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has(TYPE_KEY)) {
          // Try to decode nested annotations
          value = readNotNull(entry.getValue(), context);
        } else {
          // Guess at value types
          value = dynamicCodex.read(entry.getValue(), context);
        }
        values.put(entry.getKey(), value);
      }
      h = new Handler(typeName, Collections.unmodifiableMap(values));
    }

    Annotation a = annotationType.cast(
        Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
            new Class<?>[] { annotationType, AnnotationInfo.class }, h));
    return a;
  }

  @Override
  public void writeNotNull(Annotation a, SerializationContext context) throws Exception {
    JsonWriter writer = context.getWriter().beginObject().name(TYPE_KEY);
    if (a instanceof AnnotationInfo) {
      // Support for UnknownAnnotation
      AnnotationInfo info = (AnnotationInfo) a;
      writer.value(info.getAnnotationTypeName());
      for (Map.Entry<String, Object> entry : info.getAnnotationValues().entrySet()) {
        writer.name(entry.getKey());
        dynamicCodex.write(entry.getValue(), context);
      }
    } else {
      // The usual case
      Class<? extends Annotation> annotationType = a.annotationType();
      writer.value(annotationType.getName());
      for (Method m : annotationType.getDeclaredMethods()) {
        m.setAccessible(true);
        Object value = m.invoke(a);
        writer.name(m.getName());
        typeContext.getCodex(m.getReturnType()).write(value, context);
      }
    }
    writer.endObject();
  }
}
