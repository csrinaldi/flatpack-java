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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.PropertyPath;
import com.getperka.flatpack.ext.PropertyPath.Receiver;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.inject.FlatPackLogger;

/**
 * Standard implementation of {@link Security}.
 */
public class PrincipalSecurity implements Security {
  static class Result {
    final boolean decision;
    final String reason;

    public Result(boolean decision, String reason) {
      this.decision = decision;
      this.reason = reason;
    }
  }

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

  /**
   * Given a {@link Principal}, {@link SecurityTarget}, and {@link SecurityAction}, the following
   * permission check is performed:
   * <ul>
   * <li>Determine the {@link GroupPermissions} that govern access to {@code target} by querying the
   * {@link SecurityPolicy}.
   * <li>Iterate over each {@link SecurityGroup} that may grant the desired action.
   * <li>If the group is a global group, query the {@link PrincipalMapper} for the global group
   * names that {@code principal} is a member of and match if the group's name is in the returned
   * list of names.
   * <li>For an entity-relative group, evaluate each property path associated with the
   * {@link SecurityGroup}. The resulting graph entities are given to
   * {@link PrincipalMapper#getPrincipals(HasUuid)} to determine which, if any, can be represented
   * by a {@link Principal}. The rule will match if the current principal is a member of the mapped
   * principals.
   * </ul>
   */
  @Override
  public boolean may(final Principal principal, SecurityTarget target, SecurityAction op) {
    Result toReturn = mayImpl(principal, target, op);
    logger.trace("{} {} to {} on {} via {}", toReturn.decision ? "Allow" : "Deny",
        principal, op, target, toReturn.reason);
    return toReturn.decision;
  }

  private Result mayImpl(final Principal principal, SecurityTarget target, SecurityAction op) {
    // Bypass for super-users, secure contexts, etc.
    if (!principalMapper.isAccessEnforced(principal, target)) {
      return new Result(true, "isAccessEnforced=false");
    }

    // Find the permissions that govern access to the requested target
    GroupPermissions permissions = securityPolicy.getPermissions(target);

    // Unsecured target, allow access
    if (permissions == null) {
      return new Result(true, "no GroupPermissions");
    }

    // Iterate over each SecurityGroup that can grant the requested action
    for (SecurityGroup group : permissions.grants(op)) {

      // Inclusive security group
      if (securityGroups.getGroupAll().equals(group)) {
        return new Result(true, group.getDescription());
      }

      // Global
      if (group.isGlobalSecurityGroup()) {
        List<String> global = principalMapper.getGlobalSecurityGroups(principal);
        if (global != null && global.contains(group.getName())) {
          return new Result(true, group.getDescription());
        }
        continue;
      }

      // Entity-relative
      final HasUuid entity = target.getEntity();
      if (entity != null) {
        final AtomicBoolean found = new AtomicBoolean();
        for (PropertyPath path : group.getPaths()) {
          path.evaluate(entity, new Receiver() {
            @Override
            public boolean receive(Object value) {
              if (!(value instanceof HasUuid)) {
                return true;
              }
              List<Principal> principals = principalMapper.getPrincipals((HasUuid) value);
              if (principals != null && principals.contains(principal)) {
                found.set(true);
                return false;
              }
              return true;
            }
          });
        }
        if (found.get()) {
          return new Result(true, group.getDescription());
        }
      }
    }
    return new Result(false, "no match");
  }
}
