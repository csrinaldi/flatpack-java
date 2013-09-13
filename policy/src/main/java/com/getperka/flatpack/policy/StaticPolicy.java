package com.getperka.flatpack.policy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Provider;

import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.SecurityPolicy;
import com.getperka.flatpack.ext.SecurityTarget;
import com.getperka.flatpack.ext.TypeContext;

public class StaticPolicy implements SecurityPolicy {
  private final Map<SecurityTarget, GroupPermissions> cache =
      new ConcurrentHashMap<SecurityTarget, GroupPermissions>();
  private final String contents;
  private StaticPolicyImpl impl;
  @Inject
  private Provider<StaticPolicyImpl> implProvider;
  @Inject
  private SecurityGroups securityGroups;
  @Inject
  private TypeContext typeContext;

  public StaticPolicy(String contents) {
    this.contents = contents;
  }

  @Override
  public GroupPermissions getPermissions(SecurityTarget target) {
    GroupPermissions toReturn = cache.get(target);
    if (toReturn != null) {
      return toReturn;
    }

    outer: while (true) {
      toReturn = maybeParse().getPermissions(target);
      if (toReturn != null) {
        break;
      }
      // Look for any inherited data
      switch (target.getKind()) {
        case ENTITY:
          target = SecurityTarget.of(target.getEntityType());
          break;
        case ENTITY_PROPERTY:
          target = SecurityTarget.of(target.getEntity());
          break;
        case PROPERTY:
          target = SecurityTarget.of(typeContext.getClass(target.getProperty()
              .getEnclosingTypeName()));
        default:
          toReturn = getDefaultPermissions();
          break outer;
      }
    }
    cache.put(target, toReturn);
    return toReturn;
  }

  /**
   * Returns {@link SecurityGroups#getPermissionsNone()}.
   */
  protected GroupPermissions getDefaultPermissions() {
    return securityGroups.getPermissionsNone();
  }

  private synchronized StaticPolicyImpl maybeParse() {
    if (impl == null) {
      if (implProvider == null) {
        throw new IllegalStateException("Not injected, must be initialized via FlatPack.create()");
      }
      impl = implProvider.get();
      impl.parse(contents);
    }
    return impl;
  }
}
