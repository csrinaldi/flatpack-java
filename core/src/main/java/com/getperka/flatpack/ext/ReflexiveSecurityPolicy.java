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

import static com.getperka.flatpack.util.FlatPackCollections.setForIteration;

import java.util.Collections;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.getperka.flatpack.security.CrudOperation;

@Singleton
public class ReflexiveSecurityPolicy implements SecurityPolicy {

  @Inject
  private SecurityGroups securityGroups;
  private GroupPermissions reflexivePermissions;

  protected ReflexiveSecurityPolicy() {}

  @Override
  public GroupPermissions getPermissions(SecurityTarget target) {
    return reflexivePermissions;
  }

  @Inject
  void inject() {
    Set<SecurityAction> allOps = setForIteration();
    for (CrudOperation op : CrudOperation.values()) {
      allOps.add(SecurityAction.of(op));
    }

    reflexivePermissions = new GroupPermissions();
    reflexivePermissions.addPermissions(securityGroups.getGroupAll(),
        Collections.singleton(SecurityAction.of(CrudOperation.READ)));
    reflexivePermissions.addPermissions(securityGroups.getGroupReflexive(), allOps);
  }
}
