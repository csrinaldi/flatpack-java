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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.RoleMapper;
import com.getperka.flatpack.ext.PrincipalMapper;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.PropertySecurity;
import com.getperka.flatpack.util.FlatPackCollections;

/**
 * Enforces {@link RolesAllowed} restrictions on entity properties.
 * <p>
 * The guide to access control:
 * <ul>
 * <li>A {@link RolesAllowed}, {@link DenyAll} or {@link PermitAll} decoration on a getter or setter
 * is definitive
 * <li>A {@link RoleDefaults} annotation is preferred over a {@link RolesAllowed} on a type
 * <li>If no class-level annotations are present, public getters are presumed to be allowed
 * <li>If no class-level role annotations are present, a public setter will inherit from its getter
 * </ul>
 */
@Singleton
public class RolePropertySecurity implements PropertySecurity {
  /**
   * Used to memoize property information.
   */
  static class PropertyRoles {
    final Set<Class<?>> getterRoles;
    final Set<String> getterRoleNames;
    final Set<Class<?>> setterRoles;
    final Set<String> setterRoleNames;

    PropertyRoles(RolePropertySecurity security, Property property) {
      Set<String> getterFallback = property.getGetter() != null
        && Modifier.isPublic(property.getGetter().getModifiers()) ?
          allRoleNames : Collections.<String> emptySet();

      getterRoleNames = security.extractRoleNames(property.getGetter(), false, getterFallback);
      getterRoles = security.extractRoles(getterRoleNames);

      Set<String> setterFallback = property.getSetter() != null
        && Modifier.isPublic(property.getSetter().getModifiers()) ?
          getterRoleNames : Collections.<String> emptySet();
      setterRoleNames = security.extractRoleNames(property.getSetter(), true, setterFallback);
      setterRoles = security.extractRoles(setterRoleNames);
    }
  }

  private interface AllRolesView {}

  private interface NoRolesView {}

  final Set<Class<?>> allRoles = Collections.<Class<?>> singleton(AllRolesView.class);

  final Set<Class<?>> noRoles = Collections.<Class<?>> singleton(NoRolesView.class);

  private PrincipalMapper principalMapper;
  private RoleMapper roleMapper;

  private final ConcurrentMap<Property, PropertyRoles> propertyRoles =
      new ConcurrentHashMap<Property, RolePropertySecurity.PropertyRoles>();

  /**
   * Requires injection.
   */
  protected RolePropertySecurity() {}

  @Override
  public Set<String> getGetterRoleNames(Property property) {
    return getPropertyRoles(property).getterRoleNames;
  }

  @Override
  public Set<String> getSetterRoleNames(Property property) {
    return getPropertyRoles(property).setterRoleNames;
  }

  @Override
  public boolean mayGet(Property property, Principal principal, HasUuid target) {
    PropertyRoles roles = getPropertyRoles(property);
    // Always enforce @DenyAll
    if (roles.getterRoles.isEmpty()) {
      return false;
    }
    if (!principalMapper.isAccessEnforced(principal, target)) {
      return true;
    }
    return checkRoles(roles.getterRoles, principalMapper.getRoles(principal));
  }

  @Override
  public boolean maySet(Property property, Principal principal, HasUuid target, Object newValue) {
    PropertyRoles roles = getPropertyRoles(property);
    // Always enforce @DenyAll
    if (roles.setterRoles.isEmpty()) {
      return false;
    }
    if (!principalMapper.isAccessEnforced(principal, target)) {
      return true;
    }
    return checkRoles(roles.setterRoles, principalMapper.getRoles(principal));
  }

  /**
   * Returns the role names associated with a method or class. This method never returns
   * {@code null}, however there are several special return values:
   * <ul>
   * <li>{@link #noRoleNames} indicates that no access information was set
   * <li>{@link #allRoleNames} indicates that access should be allowed for all roles
   * <li>An empty set indicates access should be denied for all roles
   * </ul>
   */
  protected Set<String> extractRoleNames(AnnotatedElement obj, boolean isSetter) {
    // Map no method to none-allowed
    if (obj == null) {
      return noRoleNames;
    }
    if (obj.isAnnotationPresent(DenyAll.class)) {
      return Collections.emptySet();
    }
    if (obj.isAnnotationPresent(PermitAll.class)) {
      return allRoleNames;
    }
    RoleDefaults defaults = obj.getAnnotation(RoleDefaults.class);
    if (defaults != null) {
      return extractRoles(isSetter ? defaults.setters() : defaults.getters());
    }
    RolesAllowed view = obj.getAnnotation(RolesAllowed.class);
    if (view != null) {
      return extractRoles(view);
    }
    return noRoleNames;
  }

  /**
   * Extract role information from a method or, optionally, its declaring class. This method will
   * also check for {@link RoleDefaults} if no role information exists on the method.
   */
  protected Set<String> extractRoleNames(Method method, boolean isSetter, Set<String> fallback) {
    if (method == null) {
      return noRoleNames;
    }

    // Look at method
    Set<String> toReturn = extractRoleNames(method, isSetter);
    if (!noRoleNames.equals(toReturn)) {
      return toReturn;
    }

    // Look at declaring class
    toReturn = extractRoleNames(method.getDeclaringClass(), isSetter);
    if (!noRoleNames.equals(toReturn)) {
      return toReturn;
    }

    return fallback;
  }

  /**
   * Determine if any of the given credential roles map onto a required view. Visible for testing.
   */
  boolean checkRoles(Collection<Class<?>> required, Collection<String> credentials) {
    // Object comparison intentional
    if (roleMapper == null || required == allRoles) {
      return true;
    }
    if (required == null || required.isEmpty()) {
      return false;
    }
    for (String cred : credentials) {
      Class<?> credView = roleMapper.mapRole(cred);
      if (credView == null) {
        continue;
      }
      if (required.contains(credView)) {
        return true;
      }
      for (Class<?> req : required) {
        if (req.isAssignableFrom(credView)) {
          return true;
        }
      }
    }
    return false;
  }

  @Inject
  void inject(PrincipalMapper principalMapper, RoleMapper roleMapper) {
    this.principalMapper = principalMapper;
    this.roleMapper = roleMapper;
  }

  private Set<String> extractRoles(RolesAllowed view) {
    Set<String> toReturn = FlatPackCollections.setForIteration();
    toReturn.addAll(Arrays.asList(view.value()));
    if (toReturn.isEmpty()) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(toReturn);
  }

  private Set<Class<?>> extractRoles(Set<String> roleNames) {
    if (roleMapper == null || roleNames == null || noRoleNames.equals(roleNames)) {
      return noRoles;
    }
    if (allRoleNames.equals(roleNames)) {
      return allRoles;
    }
    Set<Class<?>> toReturn = FlatPackCollections.setForIteration();
    for (String name : roleNames) {
      Class<?> roleClass = roleMapper.mapRole(name);
      if (roleClass != null) {
        toReturn.add(roleClass);
      }
    }
    return Collections.unmodifiableSet(toReturn);
  }

  private PropertyRoles getPropertyRoles(Property property) {
    PropertyRoles toReturn = propertyRoles.get(property);
    if (toReturn == null) {
      synchronized (this) {
        toReturn = new PropertyRoles(this, property);
        propertyRoles.put(property, toReturn);
      }
    }
    return toReturn;
  }
}
