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

import static com.getperka.flatpack.security.CrudOperation.CREATE_ACTION;
import static com.getperka.flatpack.security.CrudOperation.UPDATE_ACTION;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.inject.PackScoped;
import com.getperka.flatpack.security.Security;
import com.getperka.flatpack.util.FlatPackCollections;

/**
 * Contains state relating to in-process deserialization.
 */
@PackScoped
public class DeserializationContext extends BaseContext {
  private final Map<UUID, HasUuid> entities = FlatPackCollections.mapForLookup();
  private final Map<HasUuid, Set<Property>> modified = FlatPackCollections.mapForLookup();
  @Inject
  private PrincipalMapper principalMapper;
  private final Set<UUID> resolved = FlatPackCollections.setForLookup();
  @Inject
  private Security security;
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
   * Apply a principal-based security check for entities that were resolved from the backing store.
   * This code will follow one or more property paths for the entity and resolve the object at the
   * end of the path into one or more principals. The test passes if the current principal is in the
   * list. Otherwise, a warning is added to the context.
   */
  public boolean checkAccess(HasUuid object) {
    // Allow newly-instantiated objects
    if (wasResolved(object) && security.may(getPrincipal(), object, CREATE_ACTION)) {
      return true;
    }
    if (security.may(getPrincipal(), object, UPDATE_ACTION)) {
      return true;
    }
    addWarning(object, "User %s does not have permission to edit this %s", getPrincipal(),
        typeContext.getPayloadName(object.getClass()));
    return false;
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
   * @param resolved {@code true} if the instance was retrieved from an {@link EntityResolver}
   */
  public void putEntity(UUID uuid, HasUuid entity, boolean resolved) {
    entities.put(uuid, entity);
    if (resolved) {
      this.resolved.add(uuid);
    }
  }

  /**
   * Returns {@code true} if the entity was obtained via an {@link EntityResolver}.
   */
  public boolean wasResolved(HasUuid entity) {
    return resolved.contains(entity.getUuid());
  }
}
