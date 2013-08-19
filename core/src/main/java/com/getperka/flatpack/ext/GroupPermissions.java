package com.getperka.flatpack.ext;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.security.Acl;
import com.getperka.flatpack.security.CrudOperation;

/**
 * Associates some number of {@link SecurityGroup SecurityGroups} with their respective
 * {@link CrudOperation} permissions.
 * 
 * @see Acl
 */
public class GroupPermissions extends BaseHasUuid {

  private Map<SecurityGroup, Set<CrudOperation>> operations = Collections.emptyMap();

  public Map<SecurityGroup, Set<CrudOperation>> getOperations() {
    return operations;
  }

  void setOperations(Map<SecurityGroup, Set<CrudOperation>> operations) {
    this.operations = operations;
  }
}
