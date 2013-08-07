package com.getperka.flatpack.security;

import java.security.Principal;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.Property;

/**
 * A no-op implementation of {@link Security} that allows all actions.
 */
public class NoSecurity implements Security {
  @Override
  public boolean may(Principal principal, HasUuid entity, CrudOperation op) {
    return true;
  }

  @Override
  public boolean may(Principal principal, HasUuid entity, Property property, CrudOperation op) {
    return true;
  }
}
