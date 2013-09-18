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

import static com.getperka.flatpack.security.CrudOperation.READ_ACTION;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.validation.ConstraintViolation;

import com.getperka.flatpack.EntityMetadata;
import com.getperka.flatpack.FlatPackEntity;
import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.PersistenceAware;
import com.getperka.flatpack.PersistenceMapper;
import com.getperka.flatpack.Visitors;
import com.getperka.flatpack.codexes.EntityCodex;
import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.SecurityTarget;
import com.getperka.flatpack.ext.SerializationContext;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.inject.PackScoped;
import com.getperka.flatpack.security.PackSecurity;
import com.getperka.flatpack.util.FlatPackCollections;
import com.google.gson.stream.JsonWriter;

/**
 * Writes a {@link FlatPackEntity} and the entities contained in a {@link SerializationContext} into
 * a {@link JsonWriter} stream.
 */
@PackScoped
public class PackWriter extends FlatPackVisitor {
  static class State {
    Set<String> dirtyPropertyNames;
    HasUuid entity;
    Property property;
  }

  @Inject
  private SerializationContext context;
  @Inject
  private Provider<EntityCodex<EntityMetadata>> metadataCodex;
  @Inject
  private PersistenceMapper persistenceMapper;
  private List<HasUuid> persistent = FlatPackCollections.listForAny();
  @Inject
  private PackSecurity security;
  private final Deque<PackWriter.State> stack = new ArrayDeque<PackWriter.State>();
  @Inject
  private TypeContext typeContext;
  @Inject
  private Visitors visitors;

  /**
   * Requires injection.
   */
  protected PackWriter() {}

  @Override
  public void endVisit(Property property, VisitorContext<Property> ctx) {
    context.popPath();
  }

  @Override
  public <Q extends HasUuid> void endVisit(Q entity, EntityCodex<Q> codex, VisitorContext<Q> ctx) {
    boolean wasEmitted = stack.pop().entity != null;
    if (wasEmitted && stack.isEmpty()) {
      try {
        context.getWriter().endObject();
      } catch (IOException e) {
        context.fail(e);
      }
    }
    context.popPath();
  }

  @Override
  public <T> boolean visit(FlatPackEntity<T> entity, Codex<T> codex,
      VisitorContext<FlatPackEntity<T>> ctx) {
    JsonWriter json = context.getWriter();
    try {
      json.beginObject();

      // data : { typeName : [ { entity }, { entity } ]
      json.name("data");
      json.beginObject();
      for (Map.Entry<Class<? extends HasUuid>, List<HasUuid>> entry : collate(
          context.getEntities()).entrySet()) {
        json.name(typeContext.describe(entry.getKey()).getTypeName());
        json.beginArray();
        for (HasUuid value : entry.getValue()) {
          if (persistenceMapper.isPersisted(value)) {
            persistent.add(value);
          }
          visitors.visit(this, value);
        }
        json.endArray();
      }
      json.endObject(); // end data

      // value : ['type', 'uuid']
      json.name("value");
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

      // Write metadata for any entities
      if (!persistent.isEmpty()) {
        json.name("metadata");
        json.beginArray();
        for (HasUuid toWrite : persistent) {
          EntityMetadata meta = new EntityMetadata();
          meta.setPersistent(true);
          meta.setUuid(toWrite.getUuid());
          visitors.visit(this, meta);
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

    return false;
  }

  @Override
  public boolean visit(Property prop, VisitorContext<Property> ctx) {
    context.pushPath("." + prop.getName());
    PackWriter.State state = stack.peek();

    // Ignore set-only properties
    if (prop.getGetter() == null) {
      return false;
    }
    // Check access
    if (!security.may(context.getPrincipal(),
        SecurityTarget.of(stack.peek().entity, prop), READ_ACTION)) {
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
    state.property = prop;
    return true;
  }

  @Override
  public <T extends HasUuid> boolean visit(T entity, EntityCodex<T> codex, VisitorContext<T> ctx) {
    context.pushPath("." + entity.getUuid());
    PackWriter.State state = new State();

    if (!security.may(context.getPrincipal(), SecurityTarget.of(entity), READ_ACTION)) {
      stack.push(state);
      return false;
    }
    // Null entity used as a hint in endVisit
    state.entity = entity;

    if (entity instanceof PersistenceAware) {
      Set<String> dirtyPropertyNames = FlatPackCollections.setForIteration();
      // Always write out uuid
      dirtyPropertyNames.add("uuid");
      dirtyPropertyNames.addAll(((PersistenceAware) entity).dirtyPropertyNames());
      state.dirtyPropertyNames = dirtyPropertyNames;
    }
    if (stack.isEmpty()) {
      try {
        context.getWriter().beginObject();
      } catch (IOException e) {
        context.fail(e);
      }
    }
    stack.push(state);
    return true;
  }

  @Override
  public <T> boolean visitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
    // Indicates that the visitor is looking at a top-level value
    if (stack.isEmpty()) {
      return true;
    }

    State state = stack.peek();
    Property prop = state.property;
    if (prop.isEmbedded()) {
      // Embedded properties should immediately traverse into the related entity
      return true;
    }

    // Write the value of the property, optionally suppressing default values
    if (prop.isSuppressDefaultValue() && codex.isDefaultValue(value)) {
      return false;
    }

    // Write the name and defer to the codex to write the JSON value
    try {
      context.getWriter().name(prop.getName() + codex.getPropertySuffix());
    } catch (IOException e) {
      context.fail(e);
    }
    codex.write(value, context);

    return false;
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