package com.getperka.flatpack.ext;

import static com.getperka.flatpack.ext.SecurityTarget.Kind.ENTITY;
import static com.getperka.flatpack.ext.SecurityTarget.Kind.ENTITY_PROPERTY;
import static com.getperka.flatpack.ext.SecurityTarget.Kind.PROPERTY;
import static com.getperka.flatpack.ext.SecurityTarget.Kind.TYPE;

import com.getperka.flatpack.HasUuid;

/**
 * A carrier object for the various kinds of things that are the target of {@link SecurityAction}.
 */
public class SecurityTarget {
  public enum Kind {
    /**
     * A {@link HasUuid} type.
     */
    TYPE,
    /**
     * A {@link Property}.
     */
    PROPERTY,
    /**
     * A specific entity.
     */
    ENTITY,
    /**
     * A property of a specific entity.
     */
    ENTITY_PROPERTY
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
}
