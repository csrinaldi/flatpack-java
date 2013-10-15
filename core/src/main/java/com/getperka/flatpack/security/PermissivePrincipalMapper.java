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
package com.getperka.flatpack.security;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import com.getperka.flatpack.HasUuid;

/**
 * A trivial implementation of {@link PrincipalMapper} that allows access to all objects.
 */
public class PermissivePrincipalMapper implements PrincipalMapper {

  /**
   * Requires injection.
   */
  protected PermissivePrincipalMapper() {}

  @Override
  public List<String> getGlobalSecurityGroups(Principal principal) {
    return Collections.emptyList();
  }

  @Override
  public List<Principal> getPrincipals(HasUuid entity) {
    return Collections.emptyList();
  }

  @Override
  public boolean isAccessEnforced(Principal principal, SecurityTarget target) {
    return false;
  }
}