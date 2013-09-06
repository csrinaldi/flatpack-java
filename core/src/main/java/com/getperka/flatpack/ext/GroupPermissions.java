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
