package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackCollections.mapForIteration;

import java.util.Map;
import java.util.Set;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.security.CrudOperation;

/**
 * Associates some number of {@link SecurityGroup SecurityGroups} with their respective
 * {@link CrudOperation} permissions.
 */
public class GroupPermissions extends BaseHasUuid {

  private Map<SecurityGroup, Set<SecurityAction>> operations = mapForIteration();

  /**
   * Returns {@code true} if members of {@code group} are allowed to perform {@code action}.
   */
  public boolean contains(SecurityGroup group, SecurityAction action) {
    Set<SecurityAction> set = operations.get(group);
    if (set == null) {
      return false;
    }

    // Exact match
    if (set.contains(action)) {
      return true;
    }

    // Handle wildcards
    for (SecurityAction test : set) {
      if (test.permit(action)) {
        return true;
      }
    }

    return false;
  }

  public Map<SecurityGroup, Set<SecurityAction>> getOperations() {
    return operations;
  }

  public void setOperations(Map<SecurityGroup, Set<SecurityAction>> operations) {
    this.operations = operations;
  }

  @Override
  public String toString() {
    return operations.toString();
  }
}
