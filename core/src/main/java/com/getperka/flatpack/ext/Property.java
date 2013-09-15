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

import static com.getperka.flatpack.util.FlatPackTypes.UTF8;
import static com.getperka.flatpack.util.FlatPackTypes.hasAnnotationWithSimpleName;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.JsonProperty;
import com.getperka.flatpack.SuppressDefaultValue;

/**
 * An immutable view of a property that should be serialized.
 */
public class Property extends BaseHasUuid {

  /**
   * Constructs {@link Property} instances.
   */
  static class Builder {
    @Inject
    private Property prop;
    @Inject
    SecurityGroups security;
    @Inject
    private TypeContext typeContext;

    Builder() {}

    public Property build() {
      Property toReturn = prop;
      prop = null;

      Method getter = toReturn.getGetter();
      Method setter = toReturn.getSetter();

      if (getter != null) {
        java.lang.reflect.Type returnType = getter.getGenericReturnType();
        toReturn.codex = typeContext.getCodex(returnType);
        analyzeAnnotations(toReturn, getter);
      } else if (setter != null) {
        java.lang.reflect.Type paramType = setter.getGenericParameterTypes()[0];
        toReturn.codex = typeContext.getCodex(paramType);
        analyzeAnnotations(toReturn, setter);
      } else {
        throw new IllegalStateException("No getter or setter");
      }
      toReturn.type = toReturn.codex.describe();

      return toReturn;
    }

    /**
     * Returns the Property object under construction.
     */
    public Property peek() {
      return prop;
    }

    public Builder withDeepTraversalOnly(boolean only) {
      prop.deepTraversalOnly = only;
      return this;
    }

    public Builder withGetter(Method getter) {
      getter.setAccessible(true);
      prop.getter = getter;

      if (prop.enclosingType == null) {
        Class<? extends HasUuid> enclosingType =
            getter.getDeclaringClass().asSubclass(HasUuid.class);
        prop.enclosingType = typeContext.describe(enclosingType);
      }

      return this;
    }

    public Builder withImpliedProperty(Property implied) {
      prop.implied = implied;
      return this;
    }

    public Builder withName(String name) {
      prop.name = name;
      return this;
    }

    public Builder withSetter(Method setter) {
      setter.setAccessible(true);
      prop.setter = setter;

      if (prop.enclosingType == null) {
        Class<? extends HasUuid> enclosingType =
            setter.getDeclaringClass().asSubclass(HasUuid.class);
        prop.enclosingType = typeContext.describe(enclosingType);
      }
      return this;
    }

    private void analyzeAnnotations(Property toReturn, AnnotatedElement method) {
      toReturn.embedded = hasAnnotationWithSimpleName(method, "Embedded");
      toReturn.suppressDefaultValue = method.isAnnotationPresent(SuppressDefaultValue.class);
    }
  }

  /**
   * Sorts Property objects by {@link #getName()}.
   */
  public static final Comparator<Property> PROPERTY_NAME_COMPARATOR = new Comparator<Property>() {
    @Override
    public int compare(Property o1, Property o2) {
      return o1.getName().compareTo(o2.getName());
    }
  };

  private Codex<?> codex;
  private boolean deepTraversalOnly;
  /**
   * This property exists for exposing annotations to callers.
   */
  private List<Annotation> docAnnotations;
  /**
   * This property is mutable by external callers. It's kind of a hack to allow the describe
   * endpoint to lazily add the doc strings.
   */
  private String docString;
  private EntityDescription enclosingType;
  private boolean embedded;
  private Method getter;
  private GroupPermissions groupPermissions;
  private Property implied;
  private String name;
  private Method setter;
  private boolean suppressDefaultValue;
  private Type type;

  @Inject
  private Property() {}

  @NoPack
  public Codex<?> getCodex() {
    return codex;
  }

  /**
   * Annotations that provide additional information about the entity. This could include
   * deprecation or JSR-303 validation constraints.
   * <p>
   * The value of this property will not influence any runtime behavior in the Flatpack
   * serialization code.
   */
  public List<Annotation> getDocAnnotations() {
    return docAnnotations;
  }

  public String getDocString() {
    return docString;
  }

  /**
   * The {@link EntityDescription} that defines the property.
   */
  public EntityDescription getEnclosingType() {
    return enclosingType;
  }

  /**
   * Returns the getter method for this Property. The returned method will have a non-{@code void}
   * return type and no parameters.
   */
  @NoPack
  public Method getGetter() {
    return getter;
  }

  /**
   * Returns the security permissions that govern access to the property.
   */
  public GroupPermissions getGroupPermissions() {
    return groupPermissions;
  }

  /**
   * When a new value is assigned to the current property in some instance, the implied property of
   * the new value should also be updated with the current instance.
   */
  public Property getImpliedProperty() {
    return implied;
  }

  /**
   * Returns the json payload name of the Property, which may differ from the bean name if a
   * {@link JsonProperty} annotation has been applied to the getter.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the optional setter for the property. The returned method will have a single parameter
   * and a {@code void} return type.
   */
  @NoPack
  public Method getSetter() {
    return setter;
  }

  /**
   * A simplified description of the property's type.
   */
  public Type getType() {
    return type;
  }

  /**
   * Returns {@code true} if the Property should be included only during a deep traversal.
   */
  public boolean isDeepTraversalOnly() {
    return deepTraversalOnly;
  }

  /**
   * Returns {@code true} if an entity Property's properties should be emitted into the owning
   * entity's properties.
   */
  public boolean isEmbedded() {
    return embedded;
  }

  /**
   * If {@code true}, non-null properties that contain the property type's default value will not be
   * serialized. For example, integer properties whose values are {@code 0} will not be serialized.
   */
  public boolean isSuppressDefaultValue() {
    return suppressDefaultValue;
  }

  public void setDocAnnotations(List<Annotation> docAnnotations) {
    this.docAnnotations = docAnnotations;
  }

  public void setDocString(String docString) {
    this.docString = docString;
  }

  public void setGroupPermissions(GroupPermissions groupPermissions) {
    this.groupPermissions = groupPermissions;
  }

  /**
   * For debugging use only.
   */
  @Override
  public String toString() {
    return getEnclosingType().getTypeName() + "." + getName() + " ::= " + getType();
  }

  @Override
  protected UUID defaultUuid() {
    if (getEnclosingType() == null || getName() == null) {
      throw new IllegalStateException();
    }
    return UUID.nameUUIDFromBytes((getEnclosingType().getTypeName() + "." + getName())
        .getBytes(UTF8));
  }

  void setDeepTraversalOnly(boolean deepTraversalOnly) {
    this.deepTraversalOnly = deepTraversalOnly;
  }

  void setEmbedded(boolean embedded) {
    this.embedded = embedded;
  }

  void setEnclosingType(EntityDescription enclosingType) {
    this.enclosingType = enclosingType;
  }

  void setImplied(Property implied) {
    this.implied = implied;
  }

  /**
   * Use for late fixups of implied properties when the OneToMany property is examined after the
   * ManyToOne relationship.
   */
  void setImpliedProperty(Property implied) {
    this.implied = implied;
  }

  void setName(String name) {
    this.name = name;
  }

  void setSuppressDefaultValue(boolean suppressDefaultValue) {
    this.suppressDefaultValue = suppressDefaultValue;
  }

  void setType(Type type) {
    this.type = type;
  }
}
