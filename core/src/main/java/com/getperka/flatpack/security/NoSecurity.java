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

import javax.inject.Singleton;

/**
 * A no-op implementation of {@link Security} and {@link SecurityPolicy} that allows all actions.
 */
@Singleton
public final class NoSecurity implements Security, SecurityPolicy {

  /**
   * Requires injection.
   */
  protected NoSecurity() {}

  @Override
  public GroupPermissions getPermissions(SecurityTarget target) {
    return null;
  }

  @Override
  public boolean may(Principal principal, SecurityTarget target, SecurityAction op) {
    return true;
  }
}
