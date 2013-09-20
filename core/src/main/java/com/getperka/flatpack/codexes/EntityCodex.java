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
package com.getperka.flatpack.codexes;

import static com.getperka.flatpack.util.FlatPackTypes.erase;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Provider;

import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.PostUnpack;
import com.getperka.flatpack.PreUnpack;
import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.DeserializationContext;
import com.getperka.flatpack.ext.EntityResolver;
import com.getperka.flatpack.ext.JsonKind;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.SecurityTarget;
import com.getperka.flatpack.ext.SerializationContext;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.ext.UpdatingCodex;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.ext.Walker;
import com.getperka.flatpack.security.CrudOperation;
import com.getperka.flatpack.security.PackSecurity;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.google.inject.TypeLiteral;

/**
 * Support for reading and writing entities that are known by {@link TypeContext}.
 * 
 * @param <T> the type of entity to encode
 */
public class EntityCodex<T extends HasUuid> extends Codex<T> {
  class PropertyWalker implements Walker<Property> {
    private final T entity;

    PropertyWalker(T entity) {
      this.entity = entity;
    }

    @Override
    public void walk(FlatPackVisitor visitor, Property prop, VisitorContext<Property> context) {
      if (visitor.visit(prop, context)) {
        @SuppressWarnings("unchecked")
        Codex<Object> codex = (Codex<Object>) prop.getCodex();
        Object value = getProperty(prop, entity);
        Object newValue = context.walkProperty(prop, codex).accept(visitor, value);
        // Object comparison intentional
        if (value != newValue) {
          setProperty(prop, entity, newValue);
        }
      }
      visitor.endVisit(prop, context);
    }
  }

  private Class<T> clazz;
  @Inject
  private EntityResolver entityResolver;
  @com.google.inject.Inject(optional = true)
  private Provider<T> provider;
  private List<Method> preUnpackMethods;
  private List<Method> postUnpackMethods;
  @Inject
  private TypeContext typeContext;
  @Inject
  private Provider<PackSecurity> security;

  protected EntityCodex() {}

  @Override
  public void acceptNotNull(FlatPackVisitor visitor, T entity, VisitorContext<T> context) {
    // See if there's a mare specific codex type that should be used instead
    @SuppressWarnings("unchecked")
    Codex<T> maybeSubtype = (Codex<T>) typeContext.getCodex(entity.getClass());
    if (this != maybeSubtype) {
      maybeSubtype.acceptNotNull(visitor, entity, context);
      return;
    }

    // Call visitValue first
    if (visitor.visitValue(entity, this, context)) {
      if (visitor.visit(entity, this, context)) {
        // Traverse all properties
        PropertyWalker walker = new PropertyWalker(entity);
        for (Property prop : typeContext.describe(clazz).getProperties()) {
          context.walkImmutable(walker).accept(visitor, prop);
        }
      }
      visitor.endVisit(entity, this, context);
    }
    visitor.endVisitValue(entity, this, context);
  }

  /**
   * Performs a minimal amount of work to create an empty stub object to fill in later.
   * 
   * @param element a JsonObject containing a {@code uuid} property.
   * @param context this method will call {@link DeserializationContext#putEntity} to store the
   *          newly-allocated entity
   */
  public T allocate(JsonElement element, DeserializationContext context) {
    JsonElement uuidElement = element.getAsJsonObject().get("uuid");
    if (uuidElement == null) {
      context.fail(new IllegalArgumentException("Data entry missing uuid:\n"
        + element.toString()));
    }
    UUID uuid = UUID.fromString(uuidElement.getAsString());
    return allocate(uuid, element, context, true);
  }

  public T allocateEmbedded(JsonElement element, DeserializationContext context) {
    return allocate(UUID.randomUUID(), element, context, false);
  }

  @Override
  public Type describe() {
    return new Type.Builder()
        .withJsonKind(JsonKind.STRING)
        .withName(typeContext.describe(clazz).getTypeName())
        .build();
  }

  public List<Method> getPostUnpackMethods() {
    return postUnpackMethods;
  }

  public List<Method> getPreUnpackMethods() {
    return preUnpackMethods;
  }

  @Override
  public String getPropertySuffix() {
    return "Uuid";
  }

  @Override
  public T readNotNull(JsonElement element, DeserializationContext context) {
    UUID uuid = UUID.fromString(element.getAsString());
    HasUuid entity = context.getEntity(uuid);
    /*
     * If the UUID is a reference to an entity that isn't in the data section, delegate to the
     * allocate() method. The entity will either be provided by an EntityResolver or a blank entity
     * will be created if possible.
     */
    if (entity == null) {
      entity = allocate(uuid, element, context, true);
    }
    try {
      return clazz.cast(entity);
    } catch (ClassCastException e) {
      throw new ClassCastException("Cannot cast a " + entity.getClass().getName()
        + " to a " + clazz.getName() + ". Duplicate UUID in data payload?");
    }
  }

  /**
   * For debugging use only.
   */
  @Override
  public String toString() {
    return clazz.getCanonicalName();
  }

  @Override
  public void writeNotNull(T object, SerializationContext context) throws IOException {
    JsonWriter writer = context.getWriter();
    writer.value(object.getUuid().toString());
  }

  /**
   * A hook point for custom subtypes to synthesize property values. The default implementation
   * invokes the method returned from {@link Property#getGetter()}.
   * 
   * @param property the property being read
   * @param target the object from which the property is being read
   * @return the property value
   * @throws Exception subclasses may delegate error handling to EntityCodex
   */
  protected Object getProperty(Property property, HasUuid target) {
    try {
      return property.getGetter() == null ? null : property.getGetter().invoke(target);
    } catch (Exception e) {
      throw new RuntimeException("Could not retrieve property value", e);
    }
  }

  /**
   * A hook point for custom subtypes to synthesize property values. The default implementation
   * invokes the method returned from {@link Property#getSetter()}.
   * 
   * @param property the property being read
   * @param target the object from which the property is being read
   * @param value the new property value
   * @throws Exception subclasses may delegate error handling to EntityCodex
   */
  protected void setProperty(Property property, T target, Object value) {
    if (property.getSetter() != null) {
      try {
        // Allow some properties (e.g. collections) to be updated in-place
        @SuppressWarnings("unchecked")
        Codex<Object> codex = (Codex<Object>) property.getCodex();
        if (codex instanceof UpdatingCodex && property.getGetter() != null) {
          Object oldValue = property.getGetter().invoke(target);
          if (oldValue != null && value != null) {
            value = ((UpdatingCodex<Object>) codex).replacementValue(oldValue, value);
          }
        }
        property.getSetter().invoke(target, value);
      } catch (Exception e) {
        throw new RuntimeException("Could not set property value", e);
      }
    }
  }

  @Inject
  void inject(TypeLiteral<T> clazz) {
    this.clazz = erase(clazz.getType());

    List<Method> pre = new ArrayList<Method>();
    List<Method> post = new ArrayList<Method>();
    // Iterate over all methods in the type and then its supertypes
    for (Class<?> lookAt = this.clazz; lookAt != null; lookAt = lookAt.getSuperclass()) {
      for (Method m : lookAt.getDeclaredMethods()) {
        Class<?>[] params = m.getParameterTypes();
        switch (params.length) {
          case 0:
            if (m.isAnnotationPresent(PreUnpack.class)) {
              m.setAccessible(true);
              pre.add(m);
            }
            if (m.isAnnotationPresent(PostUnpack.class)) {
              m.setAccessible(true);
              post.add(m);
            }
            break;
          case 1:
            if (m.isAnnotationPresent(PreUnpack.class) && params[0].equals(JsonObject.class)) {
              m.setAccessible(true);
              pre.add(m);
            }
            break;
        }
      }
    }
    // Reverse the list to call supertype methods first
    Collections.reverse(pre);
    Collections.reverse(post);
    preUnpackMethods = pre.isEmpty() ? Collections.<Method> emptyList() :
        Collections.unmodifiableList(pre);
    postUnpackMethods = post.isEmpty() ? Collections.<Method> emptyList() :
        Collections.unmodifiableList(post);
  }

  private T allocate(UUID uuid, JsonElement element, DeserializationContext context,
      boolean useResolvers) {
    T toReturn = null;
    boolean resolved = false;

    // Possibly delegate to injected resolvers
    if (useResolvers) {
      try {
        toReturn = entityResolver.resolve(clazz, uuid);
      } catch (Exception e) {
        context.fail(e);
      }
      if (toReturn != null) {
        resolved = true;
      }
    }

    // Otherwise try to construct a new instance
    if (toReturn == null
      && provider != null
      && security.get().may(
          context.getPrincipal(), SecurityTarget.of(clazz), CrudOperation.CREATE_ACTION)) {
      toReturn = provider.get();
      toReturn.setUuid(uuid);
    }

    context.putEntity(uuid, toReturn, resolved);

    return toReturn;
  }

}