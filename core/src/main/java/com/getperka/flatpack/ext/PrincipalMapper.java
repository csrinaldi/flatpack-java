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

import java.security.Principal;
import java.util.List;

import com.getperka.flatpack.HasUuid;

/**
 * Provides Flatpack with a mapping from entities to identities that may edit the entities.
 */
public interface PrincipalMapper {
  /**
   * Return all principals that are allowed to edit the entity or any entities that have a simple
   * path reference to the object.
   */
  List<Principal> getPrincipals(HasUuid entity);

  /**
   * Allows entity access restrictions to be bypassed for super-users or specific entities.
   */
  boolean isAccessEnforced(Principal principal, HasUuid entity);
}
