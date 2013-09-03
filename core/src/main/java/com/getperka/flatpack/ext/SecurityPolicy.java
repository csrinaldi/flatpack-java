package com.getperka.flatpack.ext;

import com.getperka.flatpack.HasUuid;

public interface SecurityPolicy {
  /**
   * Allows the security policy to declared a fail-open or fail-closed behavior when no permissions
   * are otherwise available.
   */
  GroupPermissions getDefaultPermissions();

  /**
   * Extract the permissions declared by a particular class or method.
   * 
   * @param allGroups the basis for resolving security group names in the acl declarations
   * @param elt the element that declares the permissions (i.e. a getter, setter, or entity class)
   * @return a summary of the permissions declared by {@code elt} or {@code null} if none have been
   *         declared
   */
  GroupPermissions getPermissions(Class<? extends HasUuid> entity);

  GroupPermissions getPermissions(Property property);
}
