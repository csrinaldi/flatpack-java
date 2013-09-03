package com.getperka.flatpack.policy.domain;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.PrincipalMapper;

public class IsPrincipalMapper implements PrincipalMapper {
  private static class FakePrincipal extends BaseHasUuid implements Principal {
    public FakePrincipal(UUID uuid) {
      setUuid(uuid);
    }

    @Override
    public String getName() {
      return getUuid().toString();
    }
  }

  @Override
  public List<String> getGlobalSecurityGroups(Principal principal) {
    // XXX Needs testing
    return null;
  }

  @Override
  public List<Principal> getPrincipals(HasUuid entity) {
    if (!(entity instanceof IsPrincipal)) {
      return null;
    }
    return Collections.<Principal> singletonList(new FakePrincipal(entity.getUuid()));
  }

  @Override
  public boolean isAccessEnforced(Principal principal, HasUuid entity) {
    return true;
  }
}
