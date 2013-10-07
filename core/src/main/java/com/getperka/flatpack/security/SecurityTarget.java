package com.getperka.flatpack.security;

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

import static com.getperka.flatpack.security.SecurityTarget.Kind.ENTITY;
import static com.getperka.flatpack.security.SecurityTarget.Kind.ENTITY_PROPERTY;
import static com.getperka.flatpack.security.SecurityTarget.Kind.GLOBAL;
import static com.getperka.flatpack.security.SecurityTarget.Kind.PROPERTY;
import static com.getperka.flatpack.security.SecurityTarget.Kind.TYPE;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.Property;

/**
 * A carrier object for the various kinds of things that are the target of {@link SecurityAction}.
 */
public class SecurityTarget {
  public enum Kind {
    /**
     * A specific entity.
     */
    ENTITY,
    /**
     * A property of a specific entity.
     */
    ENTITY_PROPERTY,
    /**
     * A non-specific target for any global permissions.
     */
    GLOBAL,
    /**
     * A {@link Property}.
     */
    PROPERTY,
    /**
     * A {@link HasUuid} type.
     */
    TYPE
  }

  private static final SecurityTarget GLOBAL_TARGET =
      new SecurityTarget(GLOBAL, null, null, null);

  public static SecurityTarget global() {
    return GLOBAL_TARGET;
  }

  public static SecurityTarget of(Class<? extends HasUuid> entityType) {
    return new SecurityTarget(TYPE, entityType, null, null);
  }

  public static SecurityTarget of(HasUuid entity) {
    return new SecurityTarget(ENTITY, entity.getClass(), entity, null);
  }

  public static SecurityTarget of(HasUuid entity, Property property) {
    return new SecurityTarget(ENTITY_PROPERTY, entity.getClass(), entity, property);
  }

  public static SecurityTarget of(Property property) {
    return new SecurityTarget(PROPERTY, null, null, property);
  }

  private final HasUuid entity;

  private final Class<? extends HasUuid> entityType;
  private final int hashCode;
  private final Kind kind;
  private final Property property;

  private SecurityTarget(Kind kind, Class<? extends HasUuid> entityType, HasUuid entity,
      Property property) {
    this.entity = entity;
    this.entityType = entityType;
    this.kind = kind;
    this.property = property;

    // Uses identityHashCode because equality is instance-based
    hashCode = System.identityHashCode(entity) * 2 + System.identityHashCode(entityType) * 3
      + System.identityHashCode(property) * 5;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SecurityTarget)) {
      return false;
    }
    SecurityTarget other = (SecurityTarget) o;

    // Instance comparisons intentional
    return entity == other.entity && entityType == other.entityType && property == other.property;
  }

  public HasUuid getEntity() {
    return entity;
  }

  public Class<? extends HasUuid> getEntityType() {
    return entityType;
  }

  public Kind getKind() {
    return kind;
  }

  public Property getProperty() {
    return property;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    String toReturn = kind.name() + " ";
    switch (kind) {
      case ENTITY:
        toReturn += entityType.getName() + " " + entity.getUuid();
        break;
      case ENTITY_PROPERTY:
        toReturn += entityType.getName() + " " + entity.getUuid() + " " + property.getName();
        break;
      case GLOBAL:
        break;
      case PROPERTY:
        toReturn += property;
        break;
      case TYPE:
        toReturn += entityType.getName();
        break;
      default:
        toReturn += "Unknown kind";
    }
    return toReturn;
  }
}
