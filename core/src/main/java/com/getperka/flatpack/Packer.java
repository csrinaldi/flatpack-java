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
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.validation.ConstraintViolation;

import org.slf4j.Logger;

import com.getperka.flatpack.codexes.EntityCodex;
import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.PropertySecurity;
import com.getperka.flatpack.ext.SerializationContext;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.ext.VisitorContext.ImmutableContext;
import com.getperka.flatpack.inject.FlatPackLogger;
import com.getperka.flatpack.inject.PackScope;
import com.getperka.flatpack.inject.PackScoped;
import com.getperka.flatpack.inject.PrettyPrint;
import com.getperka.flatpack.util.FlatPackCollections;
import com.getperka.flatpack.util.IoObserver;
import com.google.gson.JsonElement;
import com.google.gson.internal.bind.JsonTreeWriter;
import com.google.gson.stream.JsonWriter;

/**
 * Writes {@link FlatPackEntity} objects into a {@link Writer}.
 */
public class Packer {
  @PackScoped
  static class PackScanner extends PackVisitor {
    @Inject
    SerializationContext context;

    @Inject
    PropertySecurity security;

    private Deque<HasUuid> stack = new ArrayDeque<HasUuid>();

    @Override
    public void endVisit(Property property, VisitorContext<Property> ctx) {
      context.popPath();
    }

    @Override
    public <T extends HasUuid> void endVisit(T entity, VisitorContext<T> ctx) {
      stack.pop();
      context.popPath();
    }

    @Override
    public boolean visit(Property property, VisitorContext<Property> ctx) {
      context.pushPath("." + property.getName());
      if (!security.mayGet(property, context.getPrincipal(), stack.peek())) {
        return false;
      }
      if (property.isDeepTraversalOnly() && !context.getTraversalMode().writeAllProperties()) {
        return false;
      }
      return true;
    }

    @Override
    public <T extends HasUuid> boolean visit(T entity, VisitorContext<T> ctx) {
      // TODO: EntitySecurity.mayRead() ?
      context.pushPath("." + entity.getUuid());
      stack.push(entity);
      return context.add(entity);
    }
  }

  @PackScoped
  static class PackWriter extends PackVisitor {
    static class State {
      Set<String> dirtyPropertyNames;
      HasUuid entity;
    }

    @Inject
    SerializationContext context;

    @Inject
    private Provider<EntityCodex<EntityMetadata>> metadataCodex;
    @Inject
    PersistenceMapper persistenceMapper;

    @Inject
    TypeContext typeContext;

    @Inject
    PropertySecurity security;

    private final Deque<State> stack = new ArrayDeque<State>();

    @Override
    public void endVisit(Property property, VisitorContext<Property> ctx) {
      context.popPath();
    }

    @Override
    public <Q extends HasUuid> void endVisit(Q entity, VisitorContext<Q> ctx) {
      stack.pop();
      context.popPath();
    }

    @Override
    public <T> boolean visit(FlatPackEntity<T> entity, VisitorContext<FlatPackEntity<T>> ctx) {
      JsonWriter json = context.getWriter();
      try {
        json.beginObject();

        // data : { typeName : [ { entity }, { entity } ]
        json.name("data");
        json.beginObject();
        for (Map.Entry<Class<? extends HasUuid>, List<HasUuid>> entry : collate(
            context.getEntities()).entrySet()) {
          json.name(typeContext.getPayloadName(entry.getKey()));
          json.beginArray();
          Codex<HasUuid> codex = typeContext.getCodex(entry.getKey());
          for (HasUuid value : entry.getValue()) {
            new ImmutableContext<HasUuid>().acceptImmutable(this, value, codex);
          }
          json.endArray();
        }
        json.endObject(); // end data

        // value : ['type', 'uuid']
        json.name("value");
        @SuppressWarnings("unchecked")
        Codex<T> codex = (Codex<T>) typeContext.getCodex(entity.getType());
        codex.write(entity.getValue(), context);

        // errors : { 'foo.bar.baz' : 'May not be null' }
        Set<ConstraintViolation<?>> violations = entity.getConstraintViolations();
        Map<String, String> errors = entity.getExtraErrors();
        if (!violations.isEmpty() || !errors.isEmpty()) {
          json.name("errors");
          json.beginObject();
          for (ConstraintViolation<?> v : violations) {
            json.name(v.getPropertyPath().toString());
            json.value(v.getMessage());
          }
          for (Map.Entry<String, String> entry : errors.entrySet()) {
            json.name(entry.getKey()).value(entry.getValue());
          }
          json.endObject(); // errors
        }

        List<HasUuid> persistent = FlatPackCollections.listForAny();

        // Write metadata for any entities
        if (!persistent.isEmpty()) {
          EntityCodex<EntityMetadata> metaCodex = metadataCodex.get();
          json.name("metadata");
          json.beginArray();
          for (HasUuid toWrite : persistent) {
            EntityMetadata meta = new EntityMetadata();
            meta.setPersistent(true);
            meta.setUuid(toWrite.getUuid());
            new ImmutableContext<EntityMetadata>().acceptImmutable(this, meta, metaCodex);
          }
          json.endArray(); // metadata
        }

        // Write extra top-level data keys, which are only used for simple side-channel data
        for (Map.Entry<String, String> entry : entity.getExtraData().entrySet()) {
          json.name(entry.getKey()).value(entry.getValue());
        }

        // Write extra warnings, some of which may be from the serialization process
        Map<UUID, String> codexWarnings = context.getWarnings();
        Map<String, String> warnings = entity.getExtraWarnings();
        if (!codexWarnings.isEmpty() || !warnings.isEmpty()) {
          json.name("warnings");
          json.beginObject();
          for (Map.Entry<UUID, String> entry : codexWarnings.entrySet()) {
            json.name(entry.getKey().toString()).value(entry.getValue());
          }
          for (Map.Entry<String, String> entry : warnings.entrySet()) {
            json.name(entry.getKey()).value(entry.getValue());
          }
          json.endObject(); // warnings
        }
        json.endObject(); // core payload

      } catch (IOException e) {
        context.fail(e);
      }

      return true;
    }

    @Override
    public boolean visit(Property prop, VisitorContext<Property> ctx) {
      context.pushPath("." + prop.getName());
      State state = stack.peek();

      // Ignore set-only properties
      if (prop.getGetter() == null) {
        return false;
      }
      // Check access
      if (!security.mayGet(prop, context.getPrincipal(), state.entity)) {
        return false;
      }
      // Ignore OneToMany type properties unless specifically requested
      if (prop.isDeepTraversalOnly() && !context.getTraversalMode().writeAllProperties()) {
        return false;
      }
      // Don't emit a redundant uuid property
      if (stack.size() > 1 && "uuid".equals(prop.getName())) {
        return false;
      }
      // Skip clean properties
      if (state.dirtyPropertyNames != null
        && !state.dirtyPropertyNames.contains(prop.getName())) {
        return false;
      }
      try {
        // Extract the value
        Object value = getProperty(prop, state.entity);

        // Figure out how to interpret the value
        @SuppressWarnings("unchecked")
        Codex<Object> codex = (Codex<Object>) prop.getCodex();

        if (prop.isEmbedded()) {
          return true;
        } else if (!(prop.isSuppressDefaultValue() && codex.isDefaultValue(value))) {
          // Write the value of the property, optionally suppressing default values
          context.getWriter().name(prop.getName() + codex.getPropertySuffix());
          codex.write(value, context);
        }
      } catch (Exception e) {
        context.fail(e);
      }
      return false;
    }

    @Override
    public <T extends HasUuid> boolean visit(T entity, VisitorContext<T> ctx) {
      State state = new State();
      state.entity = entity;

      if (entity instanceof PersistenceAware) {
        Set<String> dirtyPropertyNames = FlatPackCollections.setForIteration();
        // Always write out uuid
        dirtyPropertyNames.add("uuid");
        dirtyPropertyNames.addAll(((PersistenceAware) entity).dirtyPropertyNames());
        state.dirtyPropertyNames = dirtyPropertyNames;
      }
      stack.push(state);
      return true;
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
    protected Object getProperty(Property property, HasUuid target) throws Exception {
      return property.getGetter() == null ? null : property.getGetter().invoke(target);
    }

    /**
     * Creates a map representing the {@code data} payload structure from an assortment of entities.
     * This method also filters out persistent objects that do not have any local mutations.
     */
    private Map<Class<? extends HasUuid>, List<HasUuid>> collate(Set<HasUuid> entities) {
      Map<Class<? extends HasUuid>, List<HasUuid>> toReturn = FlatPackCollections
          .mapForIteration();

      for (HasUuid entity : entities) {
        Class<? extends HasUuid> key = entity.getClass();

        // Ignore any dirty-tracking entity with no mutations
        if (entity instanceof PersistenceAware) {
          PersistenceAware maybeDirty = (PersistenceAware) entity;
          if (maybeDirty.wasPersistent() && maybeDirty.dirtyPropertyNames().isEmpty()) {
            continue;
          }
        }

        List<HasUuid> list = toReturn.get(key);
        if (list == null) {
          list = FlatPackCollections.listForAny();
          toReturn.put(key, list);
        }
        list.add(entity);
      }

      return toReturn;
    }
  }

  @Inject
  private Provider<SerializationContext> contexts;
  @Inject
  @FlatPackLogger
  private Logger logger;
  @Inject
  private PackScope packScope;
  @Inject
  private PersistenceMapper persistenceMapper;
  @Inject
  private TypeContext typeContext;
  @Inject
  private IoObserver ioObserver;
  @Inject
  @PrettyPrint
  private boolean prettyPrint;

  @Inject
  VisitorSupport visitorSupport;

  @Inject
  Provider<PackScanner> scanners;

  @Inject
  Provider<PackWriter> writers;

  protected Packer() {}

  /**
   * Pack the given entity into a json structure. If the entity is to be immediately written to a
   * stream, consider using {@link #pack(FlatPackEntity, Writer)} instead.
   * 
   * @param entity the entity to serialize
   * @return a json representation of the entity
   */
  public JsonElement pack(FlatPackEntity<?> entity) throws IOException {
    JsonTreeWriter json = new JsonTreeWriter();
    JsonElement toReturn;
    json.setSerializeNulls(false);
    packScope.enter().withEntity(entity).withJsonWriter(json);
    try {
      SerializationContext context = contexts.get();
      doPack(entity, context);
      toReturn = json.get();
      context.runPostWork();
      context.close();
    } finally {
      packScope.exit();
    }
    return toReturn;
  }

  /**
   * Write the given entity into a {@link Writer}.
   * 
   * @param entity the entity to write
   * @param out the destination output which will be closed by this method
   */
  public void pack(FlatPackEntity<?> entity, Writer out) throws IOException {
    out = ioObserver.observe(out);
    JsonWriter json = new JsonWriter(out);
    json.setSerializeNulls(false);
    if (prettyPrint) {
      json.setIndent("  ");
    }

    packScope.enter().withEntity(entity).withJsonWriter(json);
    try {
      SerializationContext context = contexts.get();
      doPack(entity, context);
      context.runPostWork();
      context.close();
    } finally {
      packScope.exit();
    }
  }

  private void doPack(FlatPackEntity<?> entity, SerializationContext context) throws IOException {
    try {
      visitorSupport.visit(scanners.get(), entity);
      visitorSupport.visit(writers.get(), entity);
    } catch (Exception e) {
      context.fail(e);
    }
  }
}
