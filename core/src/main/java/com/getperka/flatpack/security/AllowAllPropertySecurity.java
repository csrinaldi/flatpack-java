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

import java.security.Principal;
import java.util.Collections;
import java.util.Set;

import javax.inject.Singleton;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.PropertySecurity;

/**
 * A no-op implementation of PropertySecurity that allows access to all properties.
 */
@Singleton
public class AllowAllPropertySecurity implements PropertySecurity {

  private final Set<String> allRoleNames = Collections.singleton("*");

  @Override
  public Set<String> getGetterRoleNames(Property property) {
    return allRoleNames;
  }

  @Override
  public Set<String> getSetterRoleNames(Property property) {
    return allRoleNames;
  }

  @Override
  public boolean mayGet(Property property, Principal principal, HasUuid target) {
    return property.getGetter() != null;
  }

  @Override
  public boolean maySet(Property property, Principal principal, HasUuid target, Object newValue) {
    return property.getSetter() != null;
  }
}
