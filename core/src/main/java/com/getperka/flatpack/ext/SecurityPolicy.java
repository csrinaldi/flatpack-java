package com.getperka.flatpack.ext;

public interface SecurityPolicy {
  /**
   * Return the set of permissions that govern the given target.
   */
  GroupPermissions getPermissions(SecurityTarget target);
}
