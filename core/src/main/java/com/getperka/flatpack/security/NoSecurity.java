package com.getperka.flatpack.security;

import java.security.Principal;

import javax.inject.Inject;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.SecurityPolicy;

/**
 * A no-op implementation of {@link Security} that allows all actions.
 */
public class NoSecurity implements Security, SecurityPolicy {

  @Inject
  private SecurityGroups securityGroups;

  NoSecurity() {}

  @Override
  public GroupPermissions getDefaultPermissions() {
    return securityGroups.getPermissionsAll();
  }

  @Override
  public GroupPermissions getPermissions(Class<? extends HasUuid> entity) {
    return null;
  }

  @Override
  public GroupPermissions getPermissions(Property property) {
    return null;
  }

  @Override
  public boolean may(Principal principal, HasUuid entity, Property property, SecurityAction op) {
    return true;
  }

  @Override
  public boolean may(Principal principal, HasUuid entity, SecurityAction op) {
    return true;
  }
}
