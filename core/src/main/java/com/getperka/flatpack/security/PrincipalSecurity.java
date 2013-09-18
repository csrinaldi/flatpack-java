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

import java.security.Principal;
import java.util.List;

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

public class PrincipalSecurity implements Security {

  @FlatPackLogger
  @Inject
  private Logger logger;
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
    if (securityGroups.getGroupAll().equals(group)) {
      return true;
    }
    if (securityGroups.getGroupEmpty().equals(group)) {
      return false;
    }

    if (group.isGlobalSecurityGroup()) {
      List<String> global = principalMapper.getGlobalSecurityGroups(principal);
      if (global != null && global.contains(group.getName())) {
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

    return toReturn[0];
  }
}
