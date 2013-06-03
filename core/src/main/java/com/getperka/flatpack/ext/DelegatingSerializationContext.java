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

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.TraversalMode;
import com.google.gson.stream.JsonWriter;

/**
 * Allows a {@link SerializationContext} to be decorated.
 */
public class DelegatingSerializationContext extends SerializationContext {
  private final SerializationContext delegate;

  public DelegatingSerializationContext(SerializationContext delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean add(HasUuid entity) {
    return delegate.add(entity);
  }

  @Override
  public void addPostWork(Callable<?> r) {
    delegate.addPostWork(r);
  }

  @Override
  public void addWarning(HasUuid entity, String format, Object... args) {
    delegate.addWarning(entity, format, args);
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

  @Override
  public void fail(Throwable e) {
    delegate.fail(e);
  }

  @Override
  public Set<HasUuid> getEntities() {
    return delegate.getEntities();
  }

  @Override
  public Principal getPrincipal() {
    return delegate.getPrincipal();
  }

  @Override
  public TraversalMode getTraversalMode() {
    return delegate.getTraversalMode();
  }

  @Override
  public Map<UUID, String> getWarnings() {
    return delegate.getWarnings();
  }

  @Override
  public JsonWriter getWriter() {
    return delegate.getWriter();
  }

  @Override
  public String popPath() {
    return delegate.popPath();
  }

  @Override
  public void pushPath(String element) {
    delegate.pushPath(element);
  }

  @Override
  public void runPostWork() {
    delegate.runPostWork();
  }

  @Override
  public String toString() {
    return delegate.toString();
  }
}
