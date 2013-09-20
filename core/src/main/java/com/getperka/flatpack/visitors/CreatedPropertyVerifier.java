package com.getperka.flatpack.visitors;
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
import java.util.concurrent.Callable;

import javax.inject.Inject;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.PostWorkOrder;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityTarget;
import com.getperka.flatpack.security.Security;
import com.getperka.flatpack.util.FlatPackTypes;

/**
 * Verifies security constraints on properties for newly-created entities. Because a property's
 * security constraints may depend on other properties in the entity that have not yet been
 * configured, this class will defer the property evaluation until after all properties have been
 * set. If the principal does not have some particular permission on the entity, the value of the
 * property will be nullified.
 */
@PostWorkOrder(300)
class CreatedPropertyVerifier implements Callable<Void> {
  private SecurityAction action;
  private Principal principal;
  private HasUuid entity;
  private Property property;
  @Inject
  private Security security;

  /**
   * Requires injection.
   */
  CreatedPropertyVerifier() {}

  @Override
  public Void call() throws Exception {
    if (!security.may(principal, SecurityTarget.of(entity, property), action)) {
      Object originalValue = FlatPackTypes.getDefaultValue(
          property.getSetter().getParameterTypes()[0]);
      property.getSetter().invoke(entity, originalValue);
    }
    return null;
  }

  public void configure(Principal principal, HasUuid entity, Property property,
      SecurityAction action) {
    this.action = action;
    this.entity = entity;
    this.principal = principal;
    this.property = property;
  }
}
