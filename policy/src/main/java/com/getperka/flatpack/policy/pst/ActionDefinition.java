package com.getperka.flatpack.policy.pst;

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

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;

import java.util.List;

import com.getperka.flatpack.policy.visitors.PolicyVisitor;
import com.getperka.flatpack.security.SecurityAction;

/**
 * Used to define {@link SecurityAction} instances.
 */
public class ActionDefinition extends PolicyNode implements HasName<ActionDefinition> {
  private List<Ident<SecurityAction>> actions = listForAny();
  private Ident<ActionDefinition> name;

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(actions);
      v.traverse(name);
    }
    v.endVisit(this);
  }

  public List<Ident<SecurityAction>> getActions() {
    return actions;
  }

  @Override
  public Ident<ActionDefinition> getName() {
    return name;
  }

  public void setActions(List<Ident<SecurityAction>> actions) {
    this.actions = actions;
  }

  @Override
  public void setName(Ident<ActionDefinition> name) {
    this.name = name;
  }

}