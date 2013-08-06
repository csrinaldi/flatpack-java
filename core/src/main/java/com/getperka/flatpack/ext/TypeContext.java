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

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;
import static com.getperka.flatpack.util.FlatPackCollections.mapForIteration;
import static com.getperka.flatpack.util.FlatPackCollections.mapForLookup;
import static com.getperka.flatpack.util.FlatPackCollections.sortedMapForIteration;
import static com.getperka.flatpack.util.FlatPackTypes.decapitalize;
import static com.getperka.flatpack.util.FlatPackTypes.erase;
import static com.getperka.flatpack.util.FlatPackTypes.flatten;
import static com.getperka.flatpack.util.FlatPackTypes.getSingleParameterization;
import static com.getperka.flatpack.util.FlatPackTypes.hasAnnotationWithSimpleName;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;

import com.getperka.flatpack.Configuration;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.JsonProperty;
import com.getperka.flatpack.JsonTypeName;
import com.getperka.flatpack.PersistenceMapper;
import com.getperka.flatpack.SparseCollection;
import com.getperka.flatpack.codexes.DynamicCodex;
import com.getperka.flatpack.util.FlatPackCollections;
import com.getperka.flatpack.util.FlatPackTypes;

/**
 * Provides access to typesystem information and vends helper objects.
 * <p>
 * Instances of TypeContext are thread-safe and intended to be long-lived.
 */
@Singleton
public class TypeContext {

  /**
   * Extract the Java bean property name from a method. Note that this does not take any
   * {@link JsonProperty} annotations into account, Getters and setters must be collated by the Java
   * method names since setters aren't generally annotated.
   */
  private static String beanPropertyName(Method m) {
    String name = m.getName();
    if (name.startsWith("is")) {
      name = name.substring(2);
    } else {
      name = name.substring(3);
    }
    return decapitalize(name);
  }

  private static boolean isBoolean(Class<?> clazz) {
    return boolean.class.equals(clazz) || Boolean.class.equals(clazz);
  }

  /**
   * Returns {@code true} for:
   * <ul>
   * <li>public Foo getFoo()</li>
   * <li>public boolean isFoo()</li>
   * <li>{@code @PermitAll} &lt;any modifier&gt; Foo getFoo()</li>
   * <li>{@code @RolesAllowed} &lt;any modifier&gt; Foo getFoo()</li>
   * </ul>
   * Ignores any method declared annotated with {@link Transient} unless {@link PermitAll} or
   * {@link RolesAllowed} is present.
   */
  private static boolean isGetter(Method m) {
    if (m.getParameterTypes().length != 0) {
      return false;
    }
    String name = m.getName();
    if (name.startsWith("get") && name.length() > 3 ||
      name.startsWith("is") && name.length() > 2 && isBoolean(m.getReturnType())) {

      if (m.isAnnotationPresent(PermitAll.class)) {
        return true;
      }
      if (m.isAnnotationPresent(RolesAllowed.class)) {
        return true;
      }
      if (hasAnnotationWithSimpleName(m, "Transient")) {
        return false;
      }
      if (!Modifier.isPrivate(m.getModifiers())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Analogous to {@link #isGetter(Method)}.
   */
  private static boolean isSetter(Method m) {
    if (m.getParameterTypes().length != 1) {
      return false;
    }
    if (!m.getName().startsWith("set")) {
      return false;
    }
    if (m.isAnnotationPresent(PermitAll.class)) {
      return true;
    }
    if (m.isAnnotationPresent(RolesAllowed.class)) {
      return true;
    }
    return !Modifier.isPrivate(m.getModifiers());
  }

  /**
   * Used to instantiate instances of
   */
  @Inject
  private Provider<Property.Builder> builderProvider;
  private final Map<String, Class<? extends HasUuid>> classes = sortedMapForIteration();
  @Inject
  private CodexMapper codexMapper;
  /**
   * A map of flattened type representations to a codex capable of handling that type.
   */
  private final Map<List<Type>, Codex<?>> codexes = mapForLookup();
  /**
   * A DynamicCodex acts as a placeholder when type information can't be determined (which should be
   * rare).
   */
  @Inject
  private DynamicCodex dynamicCodex;
  private Logger logger;
  @Inject
  private PersistenceMapper persistenceMapper;
  private final Map<Class<?>, List<Property>> properties = mapForLookup();

  @Inject
  protected TypeContext() {}

  /**
   * Returns {@code true} if a {@link PersistenceMapper} is registered that provides persistence
   * metadata for the requested type.
   */
  public boolean canPersist(Class<?> clazz) {
    if (HasUuid.class.isAssignableFrom(clazz)) {
      return persistenceMapper.canPersist(clazz.asSubclass(HasUuid.class));
    }
    return false;
  }

  /**
   * Examine a class and return {@link Property} helpers that describe all JSON properties that the
   * type is expected to interact with. Calls to this method are cached in the instance of
   * {@link TypeContext}.
   */
  public synchronized List<Property> extractProperties(Class<?> clazz) {
    // No properties on Object.class. Play nicely in case of null value.
    if (clazz == null || Object.class.equals(clazz)) {
      return Collections.emptyList();
    }

    // Cache check
    List<Property> toReturn = properties.get(clazz);
    if (toReturn != null) {
      return toReturn;
    }

    toReturn = listForAny();

    // Protect the return value and cache it
    List<Property> unmodifiable = Collections.unmodifiableList(toReturn);
    properties.put(clazz, unmodifiable);

    // Start by collecting all supertype properties
    toReturn.addAll(extractProperties(clazz.getSuperclass()));

    // Link implied properties after all other properties have been stubbed out
    Map<Property.Builder, String> impliedPropertiesToLink = FlatPackCollections.mapForIteration();

    // Examine each declared method on the type and assemble Property objects
    Map<String, Property.Builder> builders = mapForIteration();
    for (Method m : clazz.getDeclaredMethods()) {
      if (isGetter(m)) {
        String beanPropertyName = beanPropertyName(m);
        Property.Builder builder = getBuilderForProperty(builders, beanPropertyName);

        // Set the getter, and update the property name
        builder.withGetter(m);
        setJsonPropertyName(builder);

        // Eagerly add the property to ensure implied properties work
        if (!toReturn.contains(builder.peek())) {
          toReturn.add(builder.peek());
        }

        // Look for SparseCollection, OneToMany or ManyToMany
        builder.withDeepTraversalOnly(isDeepTraversalOnly(m));
        /*
         * Disable traversal of Implied / OneToMany properties unless requested. Also wire up the
         * implication relationships between properties in the two models after all Properties have
         * been constructed.
         */
        String impliedPropertyName = getImpliedPropertyName(m);
        if (impliedPropertyName != null) {
          impliedPropertiesToLink.put(builder, impliedPropertyName);
        }
      } else if (isSetter(m)) {
        Property.Builder builder = getBuilderForProperty(builders, beanPropertyName(m));
        builder.withSetter(m);
        setJsonPropertyName(builder);
      }
    }

    // Wire the implied properties in the current class
    for (Map.Entry<Property.Builder, String> entry : impliedPropertiesToLink.entrySet()) {
      Property.Builder builder = entry.getKey();
      String impliedPropertyName = entry.getValue();
      Method getter = builder.peek().getGetter();
      Type elementType = getSingleParameterization(getter.getGenericReturnType(), Collection.class);

      if (elementType == null) {
        logger.error("Method {}.{} defines a OneToMany / Implies relationship but the " +
          "return type is not a Collection", clazz.getName(), getter.getName());
      } else {
        Class<?> otherModel = erase(elementType);
        for (Property otherProperty : extractProperties(otherModel)) {
          if (otherProperty.getName().equals(impliedPropertyName)) {
            builder.withImpliedProperty(otherProperty);
            otherProperty.setImpliedProperty(builder.peek());
            break;
          }
        }
      }
    }

    // Finish construction
    for (Property.Builder builder : builders.values()) {
      Property p = builder.build();
      if (!toReturn.contains(p)) {
        toReturn.add(p);
      }
    }

    return unmodifiable;
  }

  /**
   * Returns a Class from a payload name or {@code null} if the type is unknown.
   * 
   * @see Configuration#getDomainPackages()
   */
  public Class<? extends HasUuid> getClass(String simplePayloadName) {
    return classes.get(simplePayloadName);
  }

  /**
   * Convenience method to provide generics alignment.
   */
  @SuppressWarnings("unchecked")
  public <T> Codex<T> getCodex(Class<? extends T> clazz) {
    return (Codex<T>) getCodex((Type) clazz);
  }

  /**
   * Return a Codex instance that can operate on the specified type.
   */
  public synchronized Codex<?> getCodex(Type type) {
    // Use a canonical representation of the type
    List<Type> flattened = flatten(type);

    Codex<?> toReturn = codexes.get(flattened);
    if (toReturn != null) {
      return toReturn;
    }

    toReturn = codexMapper.getCodex(this, type);

    if (toReturn == null) {
      toReturn = dynamicCodex;
    }

    codexes.put(flattened, toReturn);
    return toReturn;
  }

  public Collection<Class<? extends HasUuid>> getEntityTypes() {
    return Collections.unmodifiableCollection(classes.values());
  }

  /**
   * Returns the "type" name used for an entity type in the {@code data} section of the payload.
   */
  public String getPayloadName(Class<?> clazz) {
    JsonTypeName override = clazz.getAnnotation(JsonTypeName.class);
    if (override != null) {
      return override.value();
    }
    return FlatPackTypes.decapitalize(clazz.getSimpleName());
  }

  /**
   * Implements a get-or-create pattern.
   */
  private Property.Builder getBuilderForProperty(Map<String, Property.Builder> builders,
      String beanPropertyName) {
    Property.Builder builder = builders.get(beanPropertyName);
    if (builder == null) {
      builder = builderProvider.get();
      builders.put(beanPropertyName, builder);
    }
    return builder;
  }

  /**
   * Extract the implied property name from an Implies or OneToMany annotation.
   */
  private String getImpliedPropertyName(Method m) {
    SparseCollection implies = m.getAnnotation(SparseCollection.class);
    if (implies != null) {
      // Treat the default value of an empty string as just a breakpoint, without implication
      return implies.value().isEmpty() ? null : implies.value();
    }

    for (Annotation a : m.getAnnotations()) {
      // Looking for a specific type to call a method on, so don't use hasAnnotation() method
      if ("javax.persistence.OneToMany".equals(a.annotationType().getName())) {
        try {
          return (String) a.annotationType().getMethod("mappedBy").invoke(a);
        } catch (Exception e) {
          logger.error("Could not extract information from @OneToMany", e);
        }
      }
    }

    return null;
  }

  private boolean isDeepTraversalOnly(Method m) {
    return m.isAnnotationPresent(SparseCollection.class)
      || hasAnnotationWithSimpleName(m, "OneToMany");
  }

  /**
   * Set the json property name of a Property, looking for annotations on the getter or setter.
   */
  private void setJsonPropertyName(Property.Builder builder) {
    Method m = builder.peek().getGetter();
    if (m == null) {
      m = builder.peek().getSetter();
    }

    JsonProperty override = m.getAnnotation(JsonProperty.class);
    if (override != null) {
      builder.withName(override.value());
    } else {
      builder.withName(beanPropertyName(m));
    }
  }
}
