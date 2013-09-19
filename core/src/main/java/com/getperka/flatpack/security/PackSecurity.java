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
import java.util.Map;

import javax.inject.Inject;

import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityTarget;
import com.getperka.flatpack.inject.PackScoped;

/**
 * Memoizes security decisions within a PackScope.
 */
@PackScoped
public class PackSecurity implements Security {
  static class Key {
    private final SecurityAction action;
    private final int hashCode;
    private final Principal principal;
    private final SecurityTarget target;

    public Key(SecurityTarget target, SecurityAction action, Principal principal) {
      this.action = action;
      this.principal = principal;
      this.target = target;

      hashCode = target.hashCode() * 3 + action.hashCode() * 5 + principal.hashCode() * 7;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Key)) {
        return false;
      }
      Key other = (Key) obj;

      return target.equals(other.target) && action.equals(other.action)
        && principal.equals(other.principal);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public String toString() {
      return principal + " " + action + " " + target;
    }
  }

  private final Map<Key, Boolean> cache = mapForLookup();
  @Inject
  private Security delegate;

  @Override
  public boolean may(Principal principal, SecurityTarget target, SecurityAction op) {
    Key key = new Key(target, op, principal);
    Boolean toReturn = cache.get(key);
    if (toReturn != null) {
      return toReturn;
    }
    toReturn = delegate.may(principal, target, op);
    cache.put(key, toReturn);
    return toReturn;
  }

}
