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
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.EntitySecurity;
import com.getperka.flatpack.ext.PrincipalMapper;
import com.getperka.flatpack.ext.PropertyPath;
import com.getperka.flatpack.ext.TypeContext;

/**
 * A simple, role-based implementation of EntitySecurity.
 */
@Singleton
public class RoleEntitySecurity implements EntitySecurity {
  /**
   * A utility class to examine the property paths available in a chain of HasUuid references to
   * determine if the current context's principal is listed.
   */
  class PropertyPathChecker implements PropertyPath.Receiver {
    private final Principal lookFor;
    private boolean result;

    PropertyPathChecker(Principal lookFor) {
      this.lookFor = lookFor;
    }

    public boolean getResult() {
      return result;
    }

    @Override
    public boolean receive(Object value) {
      if (value instanceof HasUuid) {
        List<Principal> principals = principalMapper.getPrincipals((HasUuid) value);
        if (principals != null && principals.contains(lookFor)) {
          result = true;
          return false;
        }
      }
      return true;
    }
  }

  private PrincipalMapper principalMapper;
  private TypeContext typeContext;

  @Override
  public boolean mayEdit(Principal principal, HasUuid entity) {
    if (principal == null) {
      return false;
    }

    // Check for superusers
    if (!principalMapper.isAccessEnforced(principal, entity)) {
      return true;
    }

    List<PropertyPath> paths = typeContext.getPrincipalPaths(entity.getClass());
    PropertyPathChecker checker = new PropertyPathChecker(principal);
    for (PropertyPath path : paths) {
      path.evaluate(entity, checker);
      if (checker.getResult()) {
        return true;
      }
    }
    return false;
  }

  @Inject
  void inject(PrincipalMapper principalMapper, TypeContext typeContext) {
    this.principalMapper = principalMapper;
    this.typeContext = typeContext;
  }
}
