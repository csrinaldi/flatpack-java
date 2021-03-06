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
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Provider;

import org.slf4j.Logger;

import com.getperka.flatpack.codexes.EntityCodex;
import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.DeserializationContext;
import com.getperka.flatpack.ext.EntityDescription;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.inject.FlatPackLogger;
import com.getperka.flatpack.inject.IgnoreUnresolvableTypes;
import com.getperka.flatpack.inject.PackScope;
import com.getperka.flatpack.util.FlatPackCollections;
import com.getperka.flatpack.util.IoObserver;
import com.getperka.flatpack.visitors.PackReader;
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
  @Inject
  private Visitors visitors;

  protected Unpacker() {}

/**
   * Read the properties of a single entity.
   * 
   * @param <T> the type of data to return
   * @param entityType the type of data to return
   * @param in a json object containing the output of a prior call to
   *          {@link Packer#append(HasUuid, Principal)
   * @param principal the identity for which the unpacking is occurring
   * @return the reified {@code entityType}
   */
  public <T extends HasUuid> T read(Class<T> entityType, JsonElement in, Principal principal) {
    if (!in.isJsonObject()) {
      throw new IllegalArgumentException("Expecting a JSON object");
    }
    packScope.enter().withPrincipal(principal);
    try {
      JsonObject chunk = in.getAsJsonObject();

      EntityCodex<T> codex = (EntityCodex<T>) typeContext.getCodex(entityType);

      DeserializationContext context = contexts.get();
      T toReturn = codex.allocate(chunk, context);

      PackReader packReader = packReaders.get();
      packReader.setPayload(chunk);
      visitors.visit(packReader, toReturn);

      return toReturn;
    } finally {
      packScope.exit();
    }
  }

  /**
   * Read the properties of a single entity.
   * 
   * @param <T> the type of data to return
   * @param entityType the type of data to return
   * @param in a Reader containing the output of a prior call to
   *          {@link Packer#append(HasUuid, Principal, java.io.Writer)}
   * @param principal the identity for which the unpacking is occurring
   * @return the reified {@code entityType}
   */
  public <T extends HasUuid> T read(Class<T> entityType, Reader in, Principal principal) {
    JsonParser parser = new JsonParser();
    JsonReader reader = new JsonReader(in);
    reader.setLenient(true);

    JsonObject chunk = parser.parse(reader).getAsJsonObject();
    return read(entityType, chunk, principal);
  }

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
      return doUnpack(returnType, new JsonTreeReader(in), principal);
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
      return doUnpack(returnType, new JsonReader(in), principal);
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

  /**
   * The guts of Unpacker.
   */
  protected <T> FlatPackEntity<T> doUnpack(Type returnType, JsonReader reader, Principal principal)
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
          EntityDescription desc = typeContext.getEntityDescription(simpleName);
          if (desc == null) {
            if (ignoreUnresolvableTypes) {
              reader.skipValue();
              continue;
            } else {
              throw new UnsupportedOperationException("Cannot resolve type " + simpleName);
            }
          } else if (Modifier.isAbstract(desc.getEntityType().getModifiers())) {
            throw new UnsupportedOperationException("A subclass of " + simpleName
              + " must be used instead");
          }
          context.pushPath("allocating " + simpleName);
          try {
            // Find the Codex for the requested entity type
            EntityCodex<?> codex = (EntityCodex<?>) typeContext.getCodex(desc.getEntityType());

            // Take the n-many property objects and stash them for later decoding
            reader.beginArray();
            while (!JsonToken.END_ARRAY.equals(reader.peek())) {
              JsonObject chunk = jsonParser.parse(reader).getAsJsonObject();
              HasUuid entity = codex.allocate(chunk, context);
              if (entity != null) {
                toReturn.addExtraEntity(entity);
                entityData.put(entity, chunk.getAsJsonObject());
              }
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

        Codex<EntityMetadata> metaCodex = typeContext.getCodex(EntityMetadata.class);
        while (!JsonToken.END_ARRAY.equals(reader.peek())) {
          JsonObject metaElement = jsonParser.parse(reader).getAsJsonObject();
          PackReader packReader = packReaders.get();
          packReader.setPayload(metaElement);
          EntityMetadata meta = new EntityMetadata();
          meta.setUuid(UUID.fromString(metaElement.get("uuid").getAsString()));
          meta = visitors.getWalkers().walkSingleton(metaCodex).accept(packReader, meta);
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
      visitors.getWalkers().walkImmutable(codex).accept(packReader, entity);
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
