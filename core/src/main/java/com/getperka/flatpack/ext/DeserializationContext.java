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

import static com.getperka.flatpack.util.FlatPackCollections.mapForLookup;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.inject.PackScoped;
import com.getperka.flatpack.security.MemoizingSecurity;
import com.getperka.flatpack.security.PrincipalMapper;
import com.getperka.flatpack.util.FlatPackCollections;

/**
 * Contains state relating to in-process deserialization.
 */
@PackScoped
public class DeserializationContext extends BaseContext {
  public enum EntitySource {
    CREATED,
    RESOLVED,
    UNKNOWN;
  }

  private final Map<UUID, HasUuid> entities = mapForLookup();
  private final Map<HasUuid, Set<Property>> modified = mapForLookup();
  @Inject
  private PrincipalMapper principalMapper;
  private final Map<UUID, EntitySource> sources = mapForLookup();
  @Inject
  private MemoizingSecurity security;

  @Inject
  private TypeContext typeContext;

  protected DeserializationContext() {}

  /**
   * Record the modification of an entity's property.
   * 
   * @return {@code true} if the Property had not been previously marked as modified
   */
  public boolean addModified(HasUuid entity, Property property) {
    Set<Property> set = modified.get(entity);
    if (set == null) {
      set = FlatPackCollections.setForIteration();
      modified.put(entity, set);
    }
    return set.add(property);
  }

  /**
   * Retrieve an entity previously provided to {@link #putEntity}.
   * 
   * @return the requested entity or {@code null} if an entity with that UUID has not been provided
   *         to {@link #putEntity(UUID, HasUuid, boolean)}
   */
  public HasUuid getEntity(UUID uuid) {
    return entities.get(uuid);
  }

  public EntitySource getEntitySource(HasUuid entity) {
    EntitySource toReturn = sources.get(entity.getUuid());
    return toReturn == null ? EntitySource.UNKNOWN : toReturn;
  }

  /**
   * Returns the Properties that were modified.
   */
  public Set<Property> getModifiedProperties(HasUuid entity) {
    Set<Property> toReturn = modified.get(entity);
    return toReturn == null ? Collections.<Property> emptySet() : toReturn;
  }

  /**
   * Stores an entity to be identified by a UUID.
   * 
   * @param uuid the UUID to assign to the entity
   * @param entity the entity to store in the context
   * @param source an indication of how the entity instance was obtained
   */
  public void putEntity(UUID uuid, HasUuid entity, EntitySource source) {
    entities.put(uuid, entity);
    sources.put(uuid, source);
  }

}
