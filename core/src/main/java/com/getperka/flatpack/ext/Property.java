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

import java.io.UnsupportedEncodingException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.management.RuntimeErrorException;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.InheritPrincipal;
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

      if (prop.enclosingTypeName == null) {
        Class<?> enclosingType = getter.getDeclaringClass();
        prop.enclosingTypeName = typeContext.getPayloadName(enclosingType);
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

      if (prop.enclosingTypeName == null) {
        Class<?> enclosingType = setter.getDeclaringClass();
        prop.enclosingTypeName = typeContext.getPayloadName(enclosingType);
      }
      return this;
    }

    private void analyzeAnnotations(Property toReturn, AnnotatedElement method) {
      toReturn.embedded = hasAnnotationWithSimpleName(method, "Embedded");
      toReturn.inheritPrincipal = method.isAnnotationPresent(InheritPrincipal.class);
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
   * This property is mutable by external callers. It's kind of a hack to allow the describe
   * endpoint to lazily add the doc strings.
   */
  private String docString;
  private String enclosingTypeName;
  private boolean embedded;
  private Method getter;
  private Set<String> getterRoleNames;
  private Property implied;
  private boolean inheritPrincipal;
  private String name;
  @Inject
  private PropertySecurity security;
  private Method setter;
  private Set<String> setterRoleNames;
  private boolean suppressDefaultValue;
  private Type type;

  @Inject
  private Property() {}

  @DenyAll
  public Codex<?> getCodex() {
    return codex;
  }

  @PermitAll
  public String getDocString() {
    return docString;
  }

  /**
   * The payload name of the type that defines the property.
   */
  @PermitAll
  public String getEnclosingTypeName() {
    return enclosingTypeName;
  }

  /**
   * Returns the getter method for this Property. The returned method will have a non-{@code void}
   * return type and no parameters.
   */
  @DenyAll
  public Method getGetter() {
    return getter;
  }

  /**
   * Returns the role names that are allowed to get the property. A value containing a single
   * asterisk means that all roles may access the property.
   */
  @PermitAll
  public Set<String> getGetterRoleNames() {
    return getterRoleNames;
  }

  /**
   * When a new value is assigned to the current property in some instance, the implied property of
   * the new value should also be updated with the current instance.
   */
  @PermitAll
  public Property getImpliedProperty() {
    return implied;
  }

  /**
   * Returns the json payload name of the Property, which may differ from the bean name if a
   * {@link JsonProperty} annotation has been applied to the getter.
   */
  @PermitAll
  public String getName() {
    return name;
  }

  /**
   * Returns the optional setter for the property. The returned method will have a single parameter
   * and a {@code void} return type.
   */
  @DenyAll
  public Method getSetter() {
    return setter;
  }

  /**
   * Return the role names that are allowed to set this property. A value containing a single
   * asterisk means that all roles may set the property.
   */
  @PermitAll
  public Set<String> getSetterRoleNames() {
    return setterRoleNames;
  }

  /**
   * A simplified description of the property's type.
   */
  @PermitAll
  public Type getType() {
    return type;
  }

  /**
   * Returns {@code true} if the Property should be included only during a deep traversal.
   */
  @PermitAll
  public boolean isDeepTraversalOnly() {
    return deepTraversalOnly;
  }

  /**
   * Returns {@code true} if an entity Property's properties should be emitted into the owning
   * entity's properties.
   */
  @PermitAll
  public boolean isEmbedded() {
    return embedded;
  }

  /**
   * Returns {@code true} if the referred entity's owner should also be considered an owner of the
   * entity that defines the Property.
   */
  @PermitAll
  public boolean isInheritPrincipal() {
    return inheritPrincipal;
  }

  /**
   * If {@code true}, non-null properties that contain the property type's default value will not be
   * serialized. For example, integer properties whose values are {@code 0} will not be serialized.
   */
  @PermitAll
  public boolean isSuppressDefaultValue() {
    return suppressDefaultValue;
  }

  public void setDocString(String docString) {
    this.docString = docString;
  }

  /**
   * For debugging use only.
   */
  @Override
  public String toString() {
    return getEnclosingTypeName() + "." + getName() + " ::= " + getType();
  }

  @Override
  protected UUID defaultUuid() {
    if (getEnclosingTypeName() == null || getName() == null) {
      throw new IllegalStateException();
    }
    try {
      return UUID.nameUUIDFromBytes((getEnclosingTypeName() + "." + getName()).getBytes(UTF8));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  void setDeepTraversalOnly(boolean deepTraversalOnly) {
    this.deepTraversalOnly = deepTraversalOnly;
  }

  void setEmbedded(boolean embedded) {
    this.embedded = embedded;
  }

  void setEnclosingTypeName(String enclosingTypeName) {
    this.enclosingTypeName = enclosingTypeName;
  }

  void setGetterRoleNames(Set<String> names) {
    this.getterRoleNames = names;
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

  void setInheritPrincipal(boolean inheritPrincipal) {
    this.inheritPrincipal = inheritPrincipal;
  }

  void setName(String name) {
    this.name = name;
  }

  void setSetterRoleNames(Set<String> names) {
    this.setterRoleNames = names;
  }

  void setSuppressDefaultValue(boolean suppressDefaultValue) {
    this.suppressDefaultValue = suppressDefaultValue;
  }

  void setType(Type type) {
    this.type = type;
  }
}
