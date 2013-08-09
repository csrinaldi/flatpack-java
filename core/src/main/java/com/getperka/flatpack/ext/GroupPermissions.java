package com.getperka.flatpack.ext;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;

import com.getperka.flatpack.security.Acl;
import com.getperka.flatpack.security.CrudOperation;

/**
 * Associates some number of {@link SecurityGroup SecurityGroups} with their respective
 * {@link CrudOperation} permissions.
 * 
 * @see Acl
 */
public class GroupPermissions {
  private static final GroupPermissions DENY_ALL = new GroupPermissions() {
    @Override
    public boolean allow(SecurityGroup group, CrudOperation op) {
      return false;
    }

    @Override
    public String toString() {
      return "Deny all";
    }
  };

  private static final GroupPermissions PERMIT_ALL = new GroupPermissions() {
    @Override
    public boolean allow(SecurityGroup group, CrudOperation op) {
      return true;
    }

    @Override
    public String toString() {
      return "Permit all";
    }
  };

  /**
   * Denies all requests.
   * 
   * @see DenyAll
   */
  public static final GroupPermissions denyAll() {
    return DENY_ALL;
  }

  /**
   * Allows all requests.
   * 
   * @see PermitAll
   */
  public static final GroupPermissions permitAll() {
    return PERMIT_ALL;
  }

  private Map<SecurityGroup, Set<CrudOperation>> operations = Collections.emptyMap();

  /**
   * Determines if the given group may perform the requested operation. If there is no mapping for
   * the group, the {@link SecurityGroup#all()} will be checked.
   */
  public boolean allow(SecurityGroup group, CrudOperation op) {
    Set<CrudOperation> set = operations.get(group);
    if (set == null) {
      set = operations.get(SecurityGroup.all());
    }
    return set == null ? false : set.contains(op);
  }

  public Map<SecurityGroup, Set<CrudOperation>> getOperations() {
    return operations;
  }

  void setOperations(Map<SecurityGroup, Set<CrudOperation>> operations) {
    this.operations = operations;
  }
}
