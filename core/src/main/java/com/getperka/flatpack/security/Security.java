package com.getperka.flatpack.security;

import java.security.Principal;

import javax.inject.Inject;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.TypeContext;

public class Security {

  @Inject
  private TypeContext typeContext;

  /**
   * Determines if the given principal may perform the requested operation on the entity.
   */
  public boolean may(Principal principal, CrudOperation op, HasUuid entity) {}

  /**
   * Determines if the given principal may perform the requested operation on the entity.
   */
  public boolean may(Principal principal, CrudOperation op, HasUuid entity, Property property) {}

}
