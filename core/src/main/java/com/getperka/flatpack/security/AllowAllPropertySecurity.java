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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.Principal;
import java.util.Set;

import javax.inject.Singleton;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.PropertySecurity;

/**
 * A no-op implementation of PropertySecurity that allows access to all public properties.
 */
@Singleton
public class AllowAllPropertySecurity implements PropertySecurity {

  @Override
  public Set<String> getGetterRoleNames(Property property) {
    return getRoleNames(property.getGetter());
  }

  /**
   * Defer to the getter, if it exists, otherwise, use the access modified on the setter.
   */
  @Override
  public Set<String> getSetterRoleNames(Property property) {
    if (property.getSetter() == null) {
      return noRoleNames;
    }
    if (property.getGetter() == null) {
      return getRoleNames(property.getSetter());
    }
    return getRoleNames(property.getGetter());
  }

  @Override
  public boolean mayGet(Property property, Principal principal, HasUuid target) {
    return allRoleNames.equals(property.getGetterRoleNames());
  }

  @Override
  public boolean maySet(Property property, Principal principal, HasUuid target, Object newValue) {
    return allRoleNames.equals(property.getSetterRoleNames());
  }

  private Set<String> getRoleNames(Method method) {
    return method != null && Modifier.isPublic(method.getModifiers()) ? allRoleNames : noRoleNames;
  }
}
