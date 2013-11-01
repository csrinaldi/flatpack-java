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

/**
 * Allows a {@link DeserializationContext} to be decorated.
 */
public class DelegatingDeserializationContext extends DeserializationContext {
  private final DeserializationContext delegate;

  public DelegatingDeserializationContext(DeserializationContext delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean addModified(HasUuid entity, Property property) {
    return delegate.addModified(entity, property);
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
  public HasUuid getEntity(UUID uuid) {
    return delegate.getEntity(uuid);
  }

  @Override
  public EntitySource getEntitySource(HasUuid entity) {
    return delegate.getEntitySource(entity);
  }

  @Override
  public Set<Property> getModifiedProperties(HasUuid entity) {
    return delegate.getModifiedProperties(entity);
  }

  @Override
  public Principal getPrincipal() {
    return delegate.getPrincipal();
  }

  @Override
  public Map<UUID, String> getWarnings() {
    return delegate.getWarnings();
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
  public void putEntity(UUID uuid, HasUuid entity, EntitySource source) {
    delegate.putEntity(uuid, entity, source);
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
