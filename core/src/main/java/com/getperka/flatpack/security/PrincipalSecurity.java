package com.getperka.flatpack.security;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;
import static com.getperka.flatpack.util.FlatPackCollections.mapForLookup;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.DeclaredSecurityGroups;
import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.PrincipalMapper;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.PropertyPath;
import com.getperka.flatpack.ext.PropertyPath.Receiver;
import com.getperka.flatpack.ext.SecurityGroup;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.inject.PackScoped;

@PackScoped
public class PrincipalSecurity implements Security {
  static class PrincipalEntityKey {
    private final Principal principal;
    private final HasUuid entity;

    public PrincipalEntityKey(Principal principal, HasUuid entity) {
      this.principal = principal;
      this.entity = entity;
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

      return principal.equals(other.principal) && entity.equals(other.entity);
    }

    @Override
    public int hashCode() {
      return principal.hashCode() * 3 + entity.hashCode() * 5;
    }
  }

  private final Map<PrincipalEntityKey, List<SecurityGroup>> membershipCache = mapForLookup();
  @Inject
  private PrincipalMapper principalMapper;
  @Inject
  private SecurityGroups securityGroups;
  @Inject
  private TypeContext typeContext;

  /**
   * Determines if the given principal may perform the requested operation on the entity.
   */
  @Override
  public boolean may(Principal principal, HasUuid entity, CrudOperation op) {
    DeclaredSecurityGroups groups = typeContext.getSecurityGroups(entity.getClass());
    if (groups == null) {
      return true;
    }
    GroupPermissions permissions = securityGroups.getPermissions(groups, entity.getClass());

    return check(principal, entity, op, permissions);
  }

  /**
   * Determines if the given principal may perform the requested operation on the entity.
   */
  @Override
  public boolean may(Principal principal, HasUuid entity, Property property, CrudOperation op) {
    GroupPermissions permissions = property.getGroupPermissions();
    if (permissions == null) {
      // Delegate to the entity-level permissions if no specific data is available
      return may(principal, entity, op);
    }

    return check(principal, entity, op, permissions);
  }

  private boolean check(Principal principal, HasUuid entity, CrudOperation op,
      GroupPermissions permissions) {
    if (permissions == null) {
      return true;
    }

    List<SecurityGroup> memberships = getMemberships(principal, entity);
    if (memberships.isEmpty()) {
      memberships = Collections.singletonList(SecurityGroup.all());
    }
    for (SecurityGroup membership : memberships) {
      if (permissions.allow(membership, op)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Determine which groups the a principal is in, relative to the specified entity. This method
   * will memoize its results, since group evaluation may require non-trivial amounts of entity
   * traversal.
   */
  private List<SecurityGroup> getMemberships(final Principal principal, HasUuid entity) {
    PrincipalEntityKey key = new PrincipalEntityKey(principal, entity);
    List<SecurityGroup> cached = membershipCache.get(key);
    if (cached != null) {
      return cached;
    }

    DeclaredSecurityGroups allGroups = typeContext.getSecurityGroups(entity.getClass());
    if (allGroups.isEmpty()) {
      return Collections.emptyList();
    }
    final List<SecurityGroup> toReturn = listForAny();

    // Add any global groups that the principal is a member of
    List<String> globalGroups = principalMapper.getGlobalSecurityGroups(principal);
    if (globalGroups != null && !globalGroups.isEmpty()) {
      for (String globalName : globalGroups) {
        SecurityGroup globalGroup = securityGroups.getGlobalGroup(globalName);
        toReturn.add(globalGroup);
      }
    }

    // Compute any explicit group mappings
    for (final SecurityGroup group : allGroups) {
      if (isMember(principal, group, entity)) {
        toReturn.add(group);
      }
    }

    return toReturn;
  }

  private boolean isMember(final Principal principal, SecurityGroup group, HasUuid entity) {
    if (SecurityGroup.all().equals(group)) {
      return true;
    }
    if (SecurityGroup.empty().equals(group)) {
      return false;
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
          if (principals.contains(principal)) {
            toReturn[0] = true;
            return false;
          }
          return true;
        }
      });
    }
    return toReturn[0];
  }
}
