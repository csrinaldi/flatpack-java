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

import static com.getperka.flatpack.util.FlatPackCollections.mapForIteration;

import java.util.Map;
import java.util.Set;

import com.getperka.flatpack.BaseHasUuid;

/**
 * Associates some number of {@link SecurityGroup SecurityGroups} with their respective
 * {@link SecurityAction} permissions.
 */
public class GroupPermissions extends BaseHasUuid {

  private Map<SecurityGroup, Set<SecurityAction>> operations = mapForIteration();

  /**
   * Returns {@code true} if members of {@code group} are allowed to perform {@code action}.
   */
  public boolean contains(SecurityGroup group, SecurityAction action) {
    Set<SecurityAction> set = operations.get(group);
    if (set == null) {
      return false;
    }

    // Exact match
    if (set.contains(action)) {
      return true;
    }

    // Handle wildcards
    for (SecurityAction test : set) {
      if (test.permit(action)) {
        return true;
      }
    }

    return false;
  }

  public Map<SecurityGroup, Set<SecurityAction>> getOperations() {
    return operations;
  }

  public void setOperations(Map<SecurityGroup, Set<SecurityAction>> operations) {
    this.operations = operations;
  }

  @Override
  public String toString() {
    return operations.toString();
  }
}
