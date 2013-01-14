package com.getperka.flatpack.ext;
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
import java.util.Set;

import com.getperka.flatpack.HasUuid;

/**
 * Controls access to individual properties.
 */
public interface PropertySecurity {

  /**
   * Returns the role names that may access the property.
   */
  Set<String> getGetterRoleNames(Property property);

  /**
   * Returns the role names that may set the property.
   */
  Set<String> getSetterRoleNames(Property property);

  /**
   * Returns {@code true} if the {@code principal} may retrieve the given property from the
   * {@code target} entity.
   * 
   * @param property the property to be retrieved
   * @param principal the principal that is accessing the property
   * @param target the entity from which the property will be retrieved
   * @return {@code true} if the property may be retrieved
   */
  boolean mayGet(Property property, Principal principal, HasUuid target);

  /**
   * Returns {@code true} if the {@code principal} may set the given property on the {@code target}
   * entity to a new value.
   * 
   * @param property the property to be set
   * @param principal the principal that is accessing the property
   * @param target the entity to which the new value will be assigned
   * @param newValue the new value to be assigned
   * @return {@code true} if the property may be set
   */
  boolean maySet(Property property, Principal principal, HasUuid target, Object newValue);

}