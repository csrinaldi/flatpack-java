package com.getperka.flatpack.policy;

import javax.inject.Inject;
import javax.inject.Provider;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.SecurityPolicy;

public class StaticPolicy implements SecurityPolicy {

  private final String contents;
  private StaticPolicyImpl impl;
  @Inject
  private Provider<StaticPolicyImpl> implProvider;
  @Inject
  private SecurityGroups securityGroups;

  public StaticPolicy(String contents) {
    this.contents = contents;
  }

  @Override
  public GroupPermissions getDefaultPermissions() {
    return securityGroups.getPermissionsAll();
  }

  @Override
  public GroupPermissions getPermissions(Class<? extends HasUuid> entity) {
    return maybeParse().getPermissions(entity);
  }

  @Override
  public GroupPermissions getPermissions(Property property) {
    return maybeParse().getPermissions(property);
  }

  StaticPolicyImpl getImpl() {
    return impl;
  }

  private synchronized StaticPolicyImpl maybeParse() {
    if (impl == null) {
      if (implProvider == null) {
        throw new IllegalStateException("Not initialized");
      }
      impl = implProvider.get();
      impl.parse(contents);
    }
    return impl;
  }
}
