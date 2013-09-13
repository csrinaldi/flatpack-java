package com.getperka.flatpack.security;

import java.security.Principal;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.SecurityPolicy;
import com.getperka.flatpack.ext.SecurityTarget;

/**
 * A no-op implementation of {@link Security} that allows all actions.
 */
@Singleton
public class NoSecurity implements Security, SecurityPolicy {

  @Inject
  private SecurityGroups securityGroups;

  NoSecurity() {}

  @Override
  public GroupPermissions getPermissions(SecurityTarget target) {
    return null;
  }

  @Override
  public boolean may(Principal principal, SecurityTarget target, SecurityAction op) {
    return true;
  }
}
