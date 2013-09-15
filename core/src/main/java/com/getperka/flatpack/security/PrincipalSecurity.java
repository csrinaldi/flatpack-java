package com.getperka.flatpack.security;
/*
 * #%L
 * FlatPack serialization code
 * %%
 * Copyright (C) 2012 - 2013 Perka Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static com.getperka.flatpack.util.FlatPackCollections.mapForLookup;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.PrincipalMapper;
import com.getperka.flatpack.ext.PropertyPath;
import com.getperka.flatpack.ext.PropertyPath.Receiver;
import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityGroup;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.SecurityPolicy;
import com.getperka.flatpack.ext.SecurityTarget;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.inject.FlatPackLogger;
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

  @FlatPackLogger
  @Inject
  private Logger logger;
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

  @Override
  public boolean may(Principal principal, SecurityTarget target, SecurityAction op) {
    if (!principalMapper.isAccessEnforced(principal, target)) {
      return true;
    }
    GroupPermissions permissions = securityPolicy.getPermissions(target);
    switch (target.getKind()) {
      case ENTITY:
      case ENTITY_PROPERTY:
        return check(principal, target.getEntity(), op, permissions);
      case TYPE:
        return check(principal, target.getEntityType(), op, permissions);
      default:
        throw new UnsupportedOperationException(target.getKind().name());
    }
  }

  private boolean check(Principal principal, Class<? extends HasUuid> entity, SecurityAction op,
      GroupPermissions permissions) {
    if (permissions == null) {
      return true;
    }

    for (SecurityGroup group : permissions.getOperations().keySet()) {
      if (permissions.contains(group, op)) {
        logger.info("Allow principal {} to {} on {} via {}",
            principal, op, entity.getName(), group.getName());
        return true;
      }
    }
    logger.info("Deny principal {} to {} on {}", principal, op, entity.getName());
    return false;
  }

  private boolean check(Principal principal, HasUuid entity, SecurityAction op,
      GroupPermissions permissions) {
    if (permissions == null) {
      return true;
    }

    for (SecurityGroup group : permissions.getOperations().keySet()) {
      if (isMember(entity, group, principal)) {
        if (permissions.contains(group, op)) {
          logger.info("Allow principal {} to {} on {} {} via {}",
              principal, op, entity.getClass().getName(), entity.getUuid(), group.getName());
          return true;
        }
      }
    }
    logger.info("Deny principal {} to {} on {} {}",
        principal, op, entity.getClass().getName(), entity.getUuid());
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

    if (group.isGlobalSecurityGroup()) {
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
