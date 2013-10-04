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

import static com.getperka.flatpack.util.FlatPackTypes.UTF8;

import java.beans.Introspector;
import java.util.UUID;

import com.getperka.flatpack.BaseHasUuid;

public class SecurityAction extends BaseHasUuid {
  private static final SecurityAction all = new SecurityAction("*", "*");

  public static SecurityAction all() {
    return all;
  }

  public static SecurityAction of(Enum<?> e) {
    String action = e.name().toLowerCase();
    String type = Introspector.decapitalize(e.getDeclaringClass().getSimpleName());
    return new SecurityAction(type, action);
  }

  public static SecurityAction of(String type, String action) {
    return new SecurityAction(type, action);
  }

  private String action;
  private String type;

  SecurityAction() {}

  private SecurityAction(String type, String action) {
    this.action = action.toLowerCase();
    this.type = Introspector.decapitalize(type);
  }

  public String getAction() {
    return action;
  }

  public String getType() {
    return type;
  }

  public boolean isActionWildcard() {
    return "*".equals(action);
  }

  public boolean isVerbWildcard() {
    return "*".equals(type);
  }

  /**
   * Returns {@code true} if a principal who possesses the current SecurityAction would also be
   * allowed to perform {@code desiredAction}.
   */
  public boolean permit(SecurityAction desiredAction) {
    if (desiredAction == null) {
      return false;
    }
    if (this.equals(desiredAction)) {
      return true;
    }
    // Allow-all action
    if ("*".equals(type)) {
      return true;
    }
    // Allow all actions of a specific type
    if (type.equals(desiredAction.type) && "*".equals(action)) {
      return true;
    }
    return false;
  }

  public void setAction(String name) {
    this.action = name;
  }

  public void setType(String type) {
    this.type = type;
  }

  /**
   * For debugging use only.
   */
  @Override
  public String toString() {
    return type + "." + action;
  }

  @Override
  protected UUID defaultUuid() {
    if (getType() == null || getAction() == null) {
      throw new IllegalStateException();
    }
    return UUID.nameUUIDFromBytes((getType() + "::" + getAction()).getBytes(UTF8));
  }
}
