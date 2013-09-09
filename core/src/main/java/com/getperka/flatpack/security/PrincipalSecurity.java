package com.getperka.flatpack.security;

import static com.getperka.flatpack.util.FlatPackCollections.mapForLookup;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.PrincipalMapper;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.PropertyPath;
import com.getperka.flatpack.ext.PropertyPath.Receiver;
import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityGroup;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.SecurityPolicy;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.inject.PackScoped;

@PackScoped
public class PrincipalSecurity implements Security {
  static class PrincipalEntityKey {
    private final HasUuid entity;
    private final SecurityGroup group;
    private final Principal principal;
    private final int hashCode;

    public PrincipalEntityKey(HasUuid entity, SecurityGroup group, Principal principal) {
      this.entity = entity;
      this.group = group;
      this.principal = principal;

      hashCode = entity.hashCode() * 3 + group.hashCode() * 5 + principal.hashCode() * 7;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof PrincipalEntityKey)) {
        return false;
      }
      PrincipalEntityKey other = (PrincipalEntityKey) obj;

      return entity.equals(other.entity) && group.equals(other.group)
        && principal.equals(other.principal);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }
  }

  private final Map<PrincipalEntityKey, Boolean> memberCache = mapForLookup();
  @Inject
  private PrincipalMapper principalMapper;
  @Inject
  private SecurityGroups securityGroups;
  @Inject
  private SecurityPolicy securityPolicy;
  @Inject
  private TypeContext typeContext;

  /**
   * Requires injection.
   */
  protected PrincipalSecurity() {}

  /**
   * Determines if the given principal may perform the requested operation on the entity.
   */
  @Override
  public boolean may(Principal principal, HasUuid entity, Property property, SecurityAction op) {
    if (!principalMapper.isAccessEnforced(principal, entity)) {
      return true;
    }
    GroupPermissions permissions = property.getGroupPermissions();
    if (permissions == null) {
      // Delegate to the entity-level permissions if no specific data is available
      return may(principal, entity, op);
    }

    return check(principal, entity, op, permissions);
  }

  /**
   * Determines if the given principal may perform the requested operation on the entity.
   */
  @Override
  public boolean may(Principal principal, HasUuid entity, SecurityAction op) {
    if (!principalMapper.isAccessEnforced(principal, entity)) {
      return true;
    }
    GroupPermissions permissions = securityPolicy.getPermissions(entity.getClass());

    return check(principal, entity, op, permissions);
  }

  private boolean check(Principal principal, HasUuid entity, SecurityAction op,
      GroupPermissions permissions) {
    if (permissions == null) {
      return true;
    }

    for (SecurityGroup group : permissions.getOperations().keySet()) {
      if (isMember(entity, group, principal)) {
        if (permissions.contains(group, op)) {
          return true;
        }
      }
    }

    return false;
  }

  private boolean isMember(HasUuid entity, SecurityGroup group, final Principal principal) {
    PrincipalEntityKey key = new PrincipalEntityKey(entity, group, principal);
    Boolean cached = memberCache.get(key);
    if (cached != null) {
      return cached;
    }

    if (securityGroups.getGroupAll().equals(group)) {
      memberCache.put(key, true);
      return true;
    }
    if (securityGroups.getGroupEmpty().equals(group)) {
      memberCache.put(key, false);
      return false;
    }

    if (group.isImplicitSecurityGroup()) {
      List<String> global = principalMapper.getGlobalSecurityGroups(principal);
      if (global != null && global.contains(group.getName())) {
        memberCache.put(key, true);
        return true;
      }
    }

    final boolean[] toReturn = { false };
    for (PropertyPath path : group.getPaths()) {
      path.evaluate(entity, new Receiver() {
        @Override
        public boolean receive(Object value) {
          if (!(value instanceof HasUuid)) {
            return true;
          }
          List<Principal> principals = principalMapper.getPrincipals((HasUuid) value);
          if (principals != null && principals.contains(principal)) {
            toReturn[0] = true;
            return false;
          }
          return true;
        }
      });
    }

    memberCache.put(key, toReturn[0]);
    return toReturn[0];
  }
}
