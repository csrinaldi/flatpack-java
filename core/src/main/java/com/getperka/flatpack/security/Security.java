package com.getperka.flatpack.security;

import java.security.Principal;

import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityTarget;

public interface Security {

  /**
   * Determines if the given principal may perform the requested operation on the target.
   */
  boolean may(Principal principal, SecurityTarget target, SecurityAction op);

}