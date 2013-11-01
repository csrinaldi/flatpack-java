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

import static com.getperka.flatpack.util.FlatPackCollections.identitySetForIteration;
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
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;

import com.getperka.flatpack.EntityMetadata;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.JsonProperty;
import com.getperka.flatpack.JsonTypeName;
import com.getperka.flatpack.PersistenceMapper;
import com.getperka.flatpack.SparseCollection;
import com.getperka.flatpack.codexes.DynamicCodex;
import com.getperka.flatpack.inject.AllTypes;
import com.getperka.flatpack.inject.FlatPackLogger;
import com.getperka.flatpack.security.SecurityPolicy;
import com.getperka.flatpack.security.SecurityTarget;
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
   * </ul>
   * Ignores any private method or those annotated with {@link NoPack}.
   */
  private static boolean isGetter(Method m) {
    if (m.getParameterTypes().length != 0) {
      return false;
    }
    String name = m.getName();
    if (name.startsWith("get") && name.length() > 3 ||
      name.startsWith("is") && name.length() > 2 && isBoolean(m.getReturnType())) {

      if (m.isAnnotationPresent(NoPack.class)) {
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
    if (m.isAnnotationPresent(NoPack.class)) {
      return false;
    }
    return !Modifier.isPrivate(m.getModifiers());
  }

  /**
   * Used to instantiate instances of {@link Property}.
   */
  @Inject
  private Provider<Property.Builder> builderProvider;
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
  private final Map<Class<? extends HasUuid>, EntityDescription> entitiesByClass = mapForIteration();
  private final Map<String, EntityDescription> entitiesByName = mapForIteration();
  /**
   * State management to make {@link #describe(Class)} behave in the reentrant case.
   */
  private Set<EntityDescription> isExtracting = identitySetForIteration();
  @FlatPackLogger
  @Inject
  private Logger logger;
  @Inject
  private PersistenceMapper persistenceMapper;
  @Inject
  private SecurityPolicy securityPolicy;

  @Inject
  protected TypeContext() {}

  /**
   * Examine a class and return an {@link EntityDescription} with introspection data. Calls to this
   * method are cached in the instance of {@link TypeContext}.
   */
  public synchronized EntityDescription describe(Class<? extends HasUuid> clazz) {
    if (clazz == null) {
      throw new NullPointerException("clazz must be non-null");
    }

    EntityDescription toReturn = entitiesByClass.get(clazz);
    if (toReturn != null) {
      return toReturn;
    }

    boolean topCall = isExtracting.isEmpty();

    // Create the type and add it to the map to short-circuit type-reference loops
    toReturn = new EntityDescription();
    isExtracting.add(toReturn);
    entitiesByClass.put(clazz, toReturn);

    // Extract the entity data
    extractOneEntity(toReturn, clazz);
    if (entitiesByName.put(toReturn.getTypeName(), toReturn) != null) {
      logger.warn("Duplicate type name {}", clazz.getName());
    }

    if (topCall) {
      finalizeEntityDescriptions();
    }
    return toReturn;
  }

  /**
   * @deprecated Use {@link #describe(Class)} and {@link EntityDescription#getProperties()} instead.
   */
  @Deprecated
  public List<Property> extractProperties(Class<? extends HasUuid> clazz) {
    return describe(clazz).getProperties();
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

  /**
   * Finds an {@link EntityDescription} based on its simple type name.
   */
  public EntityDescription getEntityDescription(String typeName) {
    return entitiesByName.get(typeName);
  }

  public Collection<EntityDescription> getEntityDescriptions() {
    return Collections.unmodifiableCollection(entitiesByClass.values());
  }

  /**
   * @deprecated Use {@link #describe(Class)} and {@link EntityDescription#getTypeName()} instead.
   */
  @Deprecated
  public String getPayloadName(Class<? extends HasUuid> clazz) {
    return describe(clazz).getTypeName();
  }

  @Inject
  void inject(@AllTypes Collection<Class<?>> allTypes) {
    if (allTypes.isEmpty()) {
      logger.warn("No unpackable classes. Will not be able to deserialize entity payloads");
      return;
    }

    EntityDescription dummy = new EntityDescription();
    isExtracting.add(dummy);

    for (Class<?> clazz : allTypes) {
      if (!HasUuid.class.isAssignableFrom(clazz)) {
        logger.warn("Ignoring type {} because it is not assignable to {}", clazz.getName(),
            HasUuid.class.getSimpleName());
        continue;
      }
      if (clazz.isInterface()) {
        logger.warn("Ignoring interface {}", clazz.getName());
        continue;
      }
      if (Modifier.isAbstract(clazz.getModifiers())) {
        logger.warn("Ignoring abstract class {}", clazz.getName());
        continue;
      }
      if (clazz.isAnonymousClass()) {
        logger.warn("Ignoring anonymous class {}", clazz.getName());
        continue;
      }

      describe(clazz.asSubclass(HasUuid.class));
    }
    // Used internally, should always be mapped
    describe(EntityMetadata.class);

    isExtracting.remove(dummy);
    finalizeEntityDescriptions();
  }

  private void extractOneEntity(EntityDescription d, Class<? extends HasUuid> clazz) {
    // Set identifying information before there's any chance of an escape
    d.setEntityType(clazz);
    d.setTypeName(getTypeName(clazz));

    EntityDescription supertype;
    List<Property> properties = listForAny();
    if (!clazz.isInterface() && HasUuid.class.isAssignableFrom(clazz.getSuperclass())) {
      // Start by collecting all supertype properties
      supertype = describe(clazz.getSuperclass().asSubclass(HasUuid.class));
      properties.addAll(supertype.getProperties());
    } else {
      supertype = null;
    }

    d.setPersistent(persistenceMapper.canPersist(clazz));
    d.setProperties(Collections.unmodifiableList(properties));
    d.setSupertype(supertype);

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
        if (!properties.contains(builder.peek())) {
          properties.add(builder.peek());
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
        Class<? extends HasUuid> otherModel = erase(elementType).asSubclass(HasUuid.class);
        List<Property> otherProperties = describe(otherModel).getProperties();
        if (otherProperties != null) {
          for (Property otherProperty : otherProperties) {
            if (otherProperty.getName().equals(impliedPropertyName)) {
              builder.withImpliedProperty(otherProperty);
              otherProperty.setImpliedProperty(builder.peek());
              break;
            }
          }
        }
      }
    }

    // Finish construction
    for (Property.Builder builder : builders.values()) {
      Property p = builder.build();
      if (!properties.contains(p)) {
        properties.add(p);
      }
    }

    // Deduplicate by name, allowing subtype properties to replace supertype properties
    Map<String, Property> propertiesByName = sortedMapForIteration();
    for (Property p : properties) {
      propertiesByName.put(p.getName(), p);
    }

    properties.clear();
    properties.addAll(propertiesByName.values());

    logger.debug("Extracted type map: {} -> {}", clazz.getCanonicalName(), d.getTypeName());
  }

  /**
   * Wire up security information. Because properties can refer to one another via group inheritance
   * it is necessary to perform this calculation after the properties have been fully constructed.
   * It's also necessary to allow for the security policy to have caused other types to be
   * extracted, hence the loop.
   */
  private void finalizeEntityDescriptions() {
    while (!isExtracting.isEmpty()) {
      // Copy out to prevent ConcurrentModificationException
      List<EntityDescription> toFinish = listForAny(isExtracting);
      isExtracting.clear();
      for (EntityDescription d : toFinish) {
        d.setGroupPermissions(securityPolicy.getPermissions(SecurityTarget.of(d.getEntityType())));
        logger.debug("{} -> {}", d.getTypeName(), d.getGroupPermissions());
        for (Property p : d.getProperties()) {
          if (p.getGroupPermissions() == null) {
            p.setGroupPermissions(securityPolicy.getPermissions(SecurityTarget.of(p)));
            logger.debug("{}.{} -> {}", d.getTypeName(), p.getName(), p.getGroupPermissions());
          }
        }
      }
    }
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

  /**
   * Returns the "type" name used for an entity type in the {@code data} section of the payload.
   */
  private String getTypeName(Class<?> clazz) {
    JsonTypeName override = clazz.getAnnotation(JsonTypeName.class);
    if (override != null) {
      return override.value();
    }
    return FlatPackTypes.decapitalize(clazz.getSimpleName());
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
