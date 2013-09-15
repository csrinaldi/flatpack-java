package com.getperka.flatpack.ext;

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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;

import com.getperka.flatpack.inject.FlatPackLogger;

/**
 * A memoizing factory for SecurityGroup instances.
 */
@Singleton
public class SecurityGroups {

  @FlatPackLogger
  @Inject
  Logger logger;
  @Inject
  SecurityPolicy securityPolicy;
  @Inject
  TypeContext typeContext;

  private final ConcurrentMap<String, SecurityGroup> allGroups =
      new ConcurrentHashMap<String, SecurityGroup>();
  private final SecurityGroup groupAll =
      new SecurityGroup("*", "All principals", Collections.<PropertyPath> emptyList());
  private final SecurityGroup groupEmpty =
      new SecurityGroup("", "No principals", Collections.<PropertyPath> emptyList());
  private final SecurityGroup groupReflexive =
      new SecurityGroup("this", "The principal that represents the entity",
          Collections.singletonList(new PropertyPath(Collections.<Property> emptyList())));

  private GroupPermissions permissionsDenyAll = new GroupPermissions() {
    @Override
    public Map<SecurityGroup, Set<SecurityAction>> getOperations() {
      return Collections.emptyMap();
    }

    @Override
    public String toString() {
      return "Deny all";
    }
  };

  private final GroupPermissions permissionsPermitAll = new GroupPermissions() {
    {
      setOperations(Collections.singletonMap(groupAll,
          Collections.singleton(new SecurityAction("*", "*"))));
    }

    @Override
    public String toString() {
      return "Permit all";
    }
  };

  /**
   * Requires injection.
   */
  SecurityGroups() {}

  /**
   * Create (or find) a group declared by a particular type.
   */
  public SecurityGroup getGroup(Class<?> owner, String name, String description,
      List<PropertyPath> paths) {
    String key = owner.getName() + ":" + name;
    SecurityGroup toReturn = allGroups.get(key);
    if (toReturn != null) {
      return toReturn;
    }

    toReturn = new SecurityGroup(name, description, paths);
    SecurityGroup existing = allGroups.putIfAbsent(key, toReturn);
    return existing == null ? toReturn : existing;
  }

  /**
   * Returns a singleton group representing all principals.
   */
  public SecurityGroup getGroupAll() {
    return groupAll;
  }

  /**
   * Returns a singleton group representing no principals.
   */
  public SecurityGroup getGroupEmpty() {
    return groupEmpty;
  }

  public SecurityGroup getGroupGlobal(String name) {
    SecurityGroup toReturn = getGroup(getClass(), name, "Global group " + name,
        Collections.<PropertyPath> emptyList());
    toReturn.setImplicitSecurityGroup(true);
    return toReturn;
  }

  /**
   * Returns a singleton group for the reflexive group (i.e. the principal that represents the
   * entity).
   */
  public SecurityGroup getGroupReflexive() {
    return groupReflexive;
  }

  /**
   * Allows all requests.
   */
  public GroupPermissions getPermissionsAll() {
    return permissionsPermitAll;
  }

  /**
   * Denies all requests.
   */
  public GroupPermissions getPermissionsNone() {
    return permissionsDenyAll;
  }
}
