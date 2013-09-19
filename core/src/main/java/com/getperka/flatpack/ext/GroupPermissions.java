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

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.util.UuidDigest;

/**
 * Associates some number of {@link SecurityGroup SecurityGroups} with their respective
 * {@link SecurityAction} permissions.
 */
public class GroupPermissions extends BaseHasUuid {

  private Map<SecurityGroup, Set<SecurityAction>> operations = new TreeMap<SecurityGroup, Set<SecurityAction>>(
      new Comparator<SecurityGroup>() {
        @Override
        public int compare(SecurityGroup a, SecurityGroup b) {
          return a.getName().compareTo(b.getName());
        }
      });

  public Map<SecurityGroup, Set<SecurityAction>> getOperations() {
    return operations;
  }

  /**
   * Returns the {@link SecurityGroups} that can grant access to the requested {@code action}.
   */
  public List<SecurityGroup> grants(SecurityAction action) {
    List<SecurityGroup> toReturn = listForAny();

    for (Map.Entry<SecurityGroup, Set<SecurityAction>> entry : operations.entrySet()) {
      for (SecurityAction maybe : entry.getValue()) {
        if (maybe.permit(action)) {
          toReturn.add(entry.getKey());
        }
      }
    }
    return toReturn;
  }

  public void setOperations(Map<SecurityGroup, Set<SecurityAction>> operations) {
    this.operations = operations;
  }

  /**
   * For debugging use only.
   */
  @Override
  public String toString() {
    return operations.toString();
  }

  @Override
  protected UUID defaultUuid() {
    UuidDigest digest = new UuidDigest();
    for (Map.Entry<SecurityGroup, Set<SecurityAction>> entry : operations.entrySet()) {
      digest.add(entry.getKey());
      for (SecurityAction value : entry.getValue()) {
        digest.add(value);
      }
    }
    return digest.digest();
  }
}
