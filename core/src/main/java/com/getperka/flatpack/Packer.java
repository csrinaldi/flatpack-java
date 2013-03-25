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
import java.security.Principal;

import javax.inject.Inject;
import javax.inject.Provider;

import org.slf4j.Logger;

import com.getperka.flatpack.ext.SerializationContext;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.inject.FlatPackLogger;
import com.getperka.flatpack.inject.PackScope;
import com.getperka.flatpack.inject.PrettyPrint;
import com.getperka.flatpack.util.IoObserver;
import com.getperka.flatpack.visitors.PackScanner;
import com.getperka.flatpack.visitors.PackWriter;
import com.google.gson.JsonElement;
import com.google.gson.internal.bind.JsonTreeWriter;
import com.google.gson.stream.JsonWriter;

/**
 * Writes {@link FlatPackEntity} objects into a {@link Writer}.
 */
public class Packer {
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
  private Provider<PackScanner> scanners;
  @Inject
  private Visitors visitorSupport;
  @Inject
  private Provider<PackWriter> writers;

  protected Packer() {}

  /**
   * Write the properties for a single entity into a json structure.
   * 
   * @param entity the entity to write
   * @param principal an optional Principal for access control
   * @return a json representation of the entity
   */
  public JsonElement append(HasUuid entity, Principal principal) throws IOException {
    JsonTreeWriter json = new JsonTreeWriter();
    packScope.enter()
        .withJsonWriter(json)
        .withPrincipal(principal)
        .withTraversalMode(TraversalMode.SPARSE);

    SerializationContext context = contexts.get();
    try {
      context.add(entity);
      visitorSupport.visit(writers.get(), entity);
      return json.get();
    } catch (Exception e) {
      context.fail(e);
      return null;
    } finally {
      packScope.exit();
    }
  }

  /**
   * Write the properties for a single entity into a {@link Writer}.
   * 
   * @param entity the entity to write
   * @param principal an optional Principal for access control
   * @param out the destination output which will not be closed by this method
   */
  public void append(HasUuid entity, Principal principal, Writer out) throws IOException {
    JsonWriter json = jsonWriter(out);

    packScope.enter()
        .withJsonWriter(json)
        .withPrincipal(principal)
        .withTraversalMode(TraversalMode.SPARSE);

    SerializationContext context = contexts.get();
    try {
      context.add(entity);
      visitorSupport.visit(writers.get(), entity);
    } catch (Exception e) {
      context.fail(e);
    } finally {
      packScope.exit();
    }
  }

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
    JsonWriter json = jsonWriter(out);

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

  protected void doPack(FlatPackEntity<?> entity, SerializationContext context) throws IOException {
    try {
      visitorSupport.visit(scanners.get(), entity);
      visitorSupport.visit(writers.get(), entity);
    } catch (Exception e) {
      context.fail(e);
    }
  }

  private JsonWriter jsonWriter(Writer out) {
    out = ioObserver.observe(out);
    JsonWriter json = new JsonWriter(out);
    json.setSerializeNulls(false);
    if (prettyPrint) {
      json.setIndent("  ");
    }
    return json;
  }
}
