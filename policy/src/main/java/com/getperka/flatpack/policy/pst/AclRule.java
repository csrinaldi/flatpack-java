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

import java.util.List;

import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityGroup;

public class AclRule extends PolicyNode {
  private Ident<SecurityGroup> groupName;
  private List<Ident<SecurityAction>> securityActions = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(groupName);
      v.traverse(securityActions);
    }
    v.endVisit(this);
  }

  public Ident<SecurityGroup> getGroupName() {
    return groupName;
  }

  public List<Ident<SecurityAction>> getSecurityActions() {
    return securityActions;
  }

  public void setGroupName(Ident<SecurityGroup> groupName) {
    this.groupName = groupName;
  }

  public void setSecurityActions(List<Ident<SecurityAction>> verbNames) {
    this.securityActions = verbNames;
  }
}