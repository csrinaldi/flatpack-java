package com.getperka.flatpack.policy;
/*
 * #%L
 * FlatPack Security Policy
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

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Provider;

import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.SecurityPolicy;
import com.getperka.flatpack.ext.SecurityTarget;
import com.getperka.flatpack.ext.TypeContext;

/**
 * A {@link SecurityPolicy} implementation that uses policy rules defined in an external file.
 */
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

  public StaticPolicy(Reader contents) throws IOException {
    StringBuilder sb = new StringBuilder();
    char[] chars = new char[4096];
    for (int read = contents.read(chars); read != -1; read = contents.read(chars)) {
      sb.append(chars, 0, read);
    }
    this.contents = sb.toString();
  }

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
