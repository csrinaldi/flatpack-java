package com.getperka.flatpack.policy.domain;

/*
 * #%L
 * FlatPack Security Policy
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
import java.util.List;
import java.util.UUID;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.PrincipalMapper;
import com.getperka.flatpack.ext.SecurityTarget;

public class IsPrincipalMapper implements PrincipalMapper {
  private static class FakePrincipal extends BaseHasUuid implements Principal {

    private String globalName;

    public FakePrincipal(UUID uuid) {
      setUuid(uuid);
    }

    public String getGlobalName() {
      return globalName;
    }

    @Override
    public String getName() {
      return getUuid().toString();
    }

    public void setGlobalName(String globalName) {
      this.globalName = globalName;
    }
  }

  @Override
  public List<String> getGlobalSecurityGroups(Principal principal) {
    FakePrincipal p = (FakePrincipal) principal;
    return p.getGlobalName() == null ? null : Collections.<String> singletonList(p.getGlobalName());
  }

  @Override
  public List<Principal> getPrincipals(HasUuid entity) {
    if (!(entity instanceof IsPrincipal)) {
      return null;
    }
    FakePrincipal toReturn = new FakePrincipal(entity.getUuid());
    if (((IsPrincipal) entity).isInGlobalGroup()) {
      toReturn.setGlobalName("global");
    }
    return Collections.<Principal> singletonList(toReturn);
  }

  @Override
  public boolean isAccessEnforced(Principal principal, SecurityTarget target) {
    return true;
  }
}
