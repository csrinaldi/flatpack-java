package com.getperka.flatpack.security;

import java.security.Principal;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.Property;

public interface Security {

  /**
   * Determines if the given principal may perform the requested operation on the entity.
   */
  boolean may(Principal principal, HasUuid entity, CrudOperation op);

  /**
   * Determines if the given principal may perform the requested operation on the entity.
   */
  boolean may(Principal principal, HasUuid entity, Property property, CrudOperation op);

}