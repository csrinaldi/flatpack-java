package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackCollections.mapForIteration;
import static com.getperka.flatpack.util.FlatPackCollections.setForIteration;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.security.CrudOperation;

public class ReflexiveSecurityPolicy implements SecurityPolicy {

  @Inject
  private SecurityGroups securityGroups;
  private GroupPermissions reflexivePermissions;

  protected ReflexiveSecurityPolicy() {}

  @Override
  public GroupPermissions getDefaultPermissions() {
    return securityGroups.getPermissionsAll();
  }

  @Override
  public GroupPermissions getPermissions(Class<? extends HasUuid> entity) {
    return reflexivePermissions;
  }

  @Override
  public GroupPermissions getPermissions(Property property) {
    return reflexivePermissions;
  }

  @Inject
  void inject() {
    Map<SecurityGroup, Set<SecurityAction>> map = mapForIteration();
    map.put(securityGroups.getGroupAll(),
        Collections.singleton(new SecurityAction(CrudOperation.READ)));

    Set<SecurityAction> allOps = setForIteration();
    for (CrudOperation op : CrudOperation.values()) {
      allOps.add(new SecurityAction(op));
    }
    map.put(securityGroups.getGroupReflexive(), allOps);

    reflexivePermissions = new GroupPermissions();
    reflexivePermissions.setOperations(Collections.unmodifiableMap(map));
  }
}
