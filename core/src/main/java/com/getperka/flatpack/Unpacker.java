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
package com.getperka.flatpack;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.security.Principal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Provider;

import org.slf4j.Logger;

import com.getperka.flatpack.codexes.EntityCodex;
import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.DeserializationContext;
import com.getperka.flatpack.ext.EntitySecurity;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.PropertySecurity;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.ext.VisitorContext.ImmutableContext;
import com.getperka.flatpack.inject.FlatPackLogger;
import com.getperka.flatpack.inject.IgnoreUnresolvableTypes;
import com.getperka.flatpack.inject.PackScope;
import com.getperka.flatpack.inject.PackScoped;
import com.getperka.flatpack.util.FlatPackCollections;
import com.getperka.flatpack.util.IoObserver;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.bind.JsonTreeReader;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/**
 * Allows {@link FlatPackEntity} instances to be restored from their serialized representations.
 * 
 * @see FlatPack#getUnpacker()
 */
public class Unpacker {
  @PackScoped
  static class PackReader extends PackVisitor {
    static class State {
      HasUuid entity;
    }

    @Inject
    DeserializationContext context;

    @Inject
    EntitySecurity entitySecurity;
    @Inject
    Provider<ImpliedPropertySetter> impliedPropertySetters;
    @Inject
    PropertySecurity security;
    private final Deque<State> stack = new ArrayDeque<State>();

    private JsonObject payload;

    @Override
    public void endVisit(Property prop, VisitorContext<Property> ctx) {
      context.pushPath("." + prop.getName());
      try {
        // Ignore properties that cannot be set
        if (prop.getSetter() == null) {
          return;
        }
        String simplePropertyName = prop.getName();
        context.pushPath("." + simplePropertyName);
        try {
          Object value;
          if (prop.isEmbedded()) {
            /*
             * Embedded objects are never referred to by uuid in the payload, so an instance will
             * need to be allocated before reading in the properties.
             */
            @SuppressWarnings("unchecked")
            EntityCodex<HasUuid> codex = (EntityCodex<HasUuid>) prop.getCodex();
            HasUuid embedded = codex.allocate(payload, context);
            embedded.setUuid(UUID.randomUUID());
            new ImmutableContext<HasUuid>().acceptImmutable(this, embedded, codex);
            value = embedded;
          } else {

            @SuppressWarnings("unchecked")
            Codex<Object> codex = (Codex<Object>) prop.getCodex();

            // merchant would become merchantUuid
            String payloadPropertyName = simplePropertyName + codex.getPropertySuffix();

            // Ignore undefined property values, while allowing explicit nullification
            if (!payload.has(payloadPropertyName)) {
              return;
            }

            value = codex.read(payload.get(payloadPropertyName), context);
          }

          if (value == null && prop.getSetter().getParameterTypes()[0].isPrimitive()) {
            // Don't try to pass a null to a primitive setter
            return;
          }

          // Verify the new value may be set
          if (!security.maySet(prop, context.getPrincipal(), stack.peek().entity, value)) {
            return;
          }

          // Perhaps set the other side of a OneToMany relationship
          Property impliedPropery = prop.getImpliedProperty();
          if (impliedPropery != null && value != null) {
            // Ensure that any linked property is also mutable
            if (!checkAccess(value, context)) {
              context.addWarning(stack.peek().entity,
                  "Ignoring property %s because the inverse relationship (%s) may not be set",
                  prop.getName(), impliedPropery.getName());
              return;
            }
            ImpliedPropertySetter setter = impliedPropertySetters.get();
            setter.setLater(impliedPropery, value, stack.peek().entity);
            context.addPostWork(setter);
          }

          // Set the value
          setProperty(prop, stack.peek().entity, value);

          // Record the value as having been set
          context.addModified(stack.peek().entity, prop);
        } catch (Exception e) {
          context.fail(e);
        } finally {
          context.popPath();
        }
      } finally {
        context.popPath();
      }
    }

    @Override
    public <Q extends HasUuid> void endVisit(Q entity, VisitorContext<Q> ctx) {
      stack.pop();
      context.popPath();
    }

    public void setPayload(JsonObject payload) {
      this.payload = payload;
    }

    @Override
    public <Q extends HasUuid> boolean visit(Q entity, VisitorContext<Q> ctx) {
      context.pushPath("." + entity.getUuid());
      if (payload.entrySet().size() == 1 && payload.has("uuid")) {
        return false;
      }
      if (!context.checkAccess(entity)) {
        return false;
      }
      State state = new State();
      state.entity = entity;
      stack.push(state);
      return true;
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
    protected void setProperty(Property property, HasUuid target, Object value) {
      if (property.getSetter() != null) {
        try {
          property.getSetter().invoke(target, value);
        } catch (Exception e) {
          throw new RuntimeException("Could not set property value", e);
        }
      }
    }

    /**
     * A fan-out to to {@link DeserializationContext#checkAccess(HasUuid)} that will accept
     * collections.
     */
    private boolean checkAccess(Object object, DeserializationContext ctx) {
      if (object instanceof HasUuid) {
        return ctx.checkAccess((HasUuid) object);
      }
      if (object instanceof Iterable) {
        for (Object obj : ((Iterable<?>) object)) {
          if (!checkAccess(obj, ctx)) {
            return false;
          }
        }
        return true;
      }
      return false;
    }
  }

  @Inject
  private Provider<DeserializationContext> contexts;
  @Inject
  private Provider<EntityCodex<EntityMetadata>> metaCodex;
  @IgnoreUnresolvableTypes
  @Inject
  private boolean ignoreUnresolvableTypes;
  @Inject
  private IoObserver ioObserver;
  @Inject
  @FlatPackLogger
  private Logger logger;
  @Inject
  private PackScope packScope;
  @Inject
  private Provider<PackReader> packReaders;
  @Inject
  private TypeContext typeContext;

  protected Unpacker() {}

  /**
   * Reify a {@link FlatPackEntity} from an in-memory json representation.
   * 
   * @param <T> the type of data to return
   * @param returnType a reference to {@code T}
   * @param in the source of the serialized data
   * @param principal the identity for which the unpacking is occurring
   * @return the reified {@link FlatPackEntity}.
   */
  public <T> FlatPackEntity<T> unpack(Type returnType, JsonElement in, Principal principal)
      throws IOException {
    packScope.enter().withPrincipal(principal);
    try {
      return unpack(returnType, new JsonTreeReader(in), principal);
    } finally {
      packScope.exit();
    }
  }

  /**
   * Reify a {@link FlatPackEntity} from its serialized form.
   * 
   * @param <T> the type of data to return
   * @param returnType a reference to {@code T}
   * @param in the source of the serialized data
   * @param principal the identity for which the unpacking is occurring
   * @return the reified {@link FlatPackEntity}.
   */
  public <T> FlatPackEntity<T> unpack(Type returnType, Reader in, Principal principal)
      throws IOException {
    in = ioObserver.observe(in);
    packScope.enter().withPrincipal(principal);
    try {
      return unpack(returnType, new JsonReader(in), principal);
    } finally {
      packScope.exit();
    }
  }

  /**
   * This method can be used with an anonymous subclass of {@link TypeReference} or with an empty
   * entity returned by a {@link FlatPackEntity} factory method.
   * 
   * @param <T> the type of data to return
   * @param returnType a reference to {@code T}
   * @param in the source of the serialized data
   * @param principal the identity for which the unpacking is occurring
   * @return the reified {@link FlatPackEntity}.
   * @see FlatPackEntity#collectionOf(Class)
   * @see FlatPackEntity#mapOf(Class, Class)
   * @see FlatPackEntity#stringMapOf(Class)
   */
  public <T> FlatPackEntity<T> unpack(TypeReference<T> returnType, Reader in,
      Principal principal) throws IOException {
    return unpack(returnType.getType(), in, principal);
  }

  private <T> FlatPackEntity<T> unpack(Type returnType, JsonReader reader, Principal principal)
      throws IOException {
    // Hold temporary state for deserialization
    DeserializationContext context = contexts.get();

    /*
     * Decoding is done as a two-pass operation since the runtime type of an allocated object cannot
     * be swizzled. The per-entity data is held as a semi-reified JsonObject to be passed off to a
     * Codex.
     */
    Map<HasUuid, JsonObject> entityData = FlatPackCollections.mapForIteration();
    // Used to populate the entityData map
    JsonParser jsonParser = new JsonParser();
    /*
     * The reader is placed in lenient mode as an aid to developers, however all output will be
     * strictly formatted.
     */
    reader.setLenient(true);

    // The return value
    @SuppressWarnings("unchecked")
    FlatPackEntity<T> toReturn = (FlatPackEntity<T>) FlatPackEntity.create(returnType, null,
        principal);
    // Stores the single, top-level value in the payload for two-pass reification
    JsonElement value = null;

    if (reader.peek().equals(JsonToken.NULL)) {
      return toReturn;
    }

    reader.beginObject();

    while (JsonToken.NAME.equals(reader.peek())) {
      String name = reader.nextName();
      if ("data".equals(name)) {
        // data : { "fooEntity" : [ { ... }, { ... } ]
        reader.beginObject();
        while (JsonToken.NAME.equals(reader.peek())) {
          // Turn "fooEntity" into the actual FooEntity class
          String simpleName = reader.nextName();
          Class<? extends HasUuid> clazz = typeContext.getClass(simpleName);
          if (clazz == null) {
            if (ignoreUnresolvableTypes) {
              reader.skipValue();
              continue;
            } else {
              throw new UnsupportedOperationException("Cannot resolve type " + simpleName);
            }
          } else if (Modifier.isAbstract(clazz.getModifiers())) {
            throw new UnsupportedOperationException("A subclass of " + simpleName
              + " must be used instead");
          }
          context.pushPath("allocating " + simpleName);
          try {
            // Find the Codex for the requested entity type
            EntityCodex<?> codex = (EntityCodex<?>) typeContext.getCodex(clazz);

            // Take the n-many property objects and stash them for later decoding
            reader.beginArray();
            while (!JsonToken.END_ARRAY.equals(reader.peek())) {
              JsonObject chunk = jsonParser.parse(reader).getAsJsonObject();
              HasUuid entity = codex.allocate(chunk, context);
              toReturn.addExtraEntity(entity);
              entityData.put(entity, chunk.getAsJsonObject());
            }
            reader.endArray();
          } finally {
            context.popPath();
          }
        }
        reader.endObject();
      } else if ("errors".equals(name)) {
        // "errors" : { "path" : "problem", "path2" : "problem2" }
        reader.beginObject();
        while (JsonToken.NAME.equals(reader.peek())) {
          String path = reader.nextName();
          if (JsonToken.STRING.equals(reader.peek()) || JsonToken.NUMBER.equals(reader.peek())) {
            toReturn.addError(path, reader.nextString());
          } else {
            reader.skipValue();
          }
        }
        reader.endObject();
      } else if ("metadata".equals(name)) {
        reader.beginArray();

        while (!JsonToken.END_ARRAY.equals(reader.peek())) {
          EntityMetadata meta = new EntityMetadata();
          JsonObject metaElement = jsonParser.parse(reader).getAsJsonObject();
          PackReader packReader = packReaders.get();
          packReader.setPayload(metaElement);
          new ImmutableContext<EntityMetadata>().acceptImmutable(packReader, meta, metaCodex.get());
          toReturn.addMetadata(meta);
        }

        reader.endArray();
      } else if ("value".equals(name)) {
        // Just stash the value element in case it occurs first
        value = jsonParser.parse(reader);
      } else if ("warnings".equals(name)) {
        // "warnings" : { "path" : "problem", "path2" : "problem2" }
        reader.beginObject();
        while (JsonToken.NAME.equals(reader.peek())) {
          String path = reader.nextName();
          if (JsonToken.STRING.equals(reader.peek()) || JsonToken.NUMBER.equals(reader.peek())) {
            toReturn.addWarning(path, reader.nextString());
          } else {
            reader.skipValue();
          }
        }
        reader.endObject();
      } else if (JsonToken.STRING.equals(reader.peek()) || JsonToken.NUMBER.equals(reader.peek())) {
        // Save off any other simple properties
        toReturn.putExtraData(name, reader.nextString());
      } else {
        // Ignore random other entries
        reader.skipValue();
      }
    }

    reader.endObject();
    reader.close();

    PackReader packReader = packReaders.get();
    for (Map.Entry<HasUuid, JsonObject> entry : entityData.entrySet()) {
      HasUuid entity = entry.getKey();
      EntityCodex<HasUuid> codex = (EntityCodex<HasUuid>) typeContext
          .getCodex(entity.getClass());
      packReader.setPayload(entry.getValue());
      new ImmutableContext<HasUuid>().acceptImmutable(packReader, entity, codex);
    }

    @SuppressWarnings("unchecked")
    Codex<T> returnCodex = (Codex<T>) typeContext.getCodex(toReturn.getType());
    toReturn.withValue(returnCodex.read(value, context));

    for (Map.Entry<UUID, String> entry : context.getWarnings().entrySet()) {
      toReturn.addWarning(entry.getKey().toString(), entry.getValue());
    }

    // Process metadata
    for (EntityMetadata meta : toReturn.getMetadata()) {
      if (meta.isPersistent()) {
        HasUuid entity = context.getEntity(meta.getUuid());
        if (entity instanceof PersistenceAware) {
          ((PersistenceAware) entity).markPersistent();
        }
      }
    }

    context.runPostWork();
    context.close();

    return toReturn;
  }
}
