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

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.policy.visitors.PolicyVisitor;

/**
 * Policy information for a single type.
 */
public class TypePolicy extends PolicyNode implements HasRootScopeName<Class<? extends HasUuid>> {
  private List<AllowBlock> allows = listForAny();
  private List<GroupBlock> groups = listForAny();
  private Ident<Class<? extends HasUuid>> name;
  private List<PropertyPolicy> policies = listForAny();
  private List<ActionDefinition> verbs = listForAny();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(allows);
      v.traverse(groups);
      v.traverse(name);
      v.traverse(policies);
      v.traverse(verbs);
    }
    v.endVisit(this);
  }

  public List<AllowBlock> getAllows() {
    return allows;
  }

  public List<GroupBlock> getGroups() {
    return groups;
  }

  @Override
  public Ident<Class<? extends HasUuid>> getName() {
    return name;
  }

  public List<PropertyPolicy> getPolicies() {
    return policies;
  }

  public List<ActionDefinition> getVerbs() {
    return verbs;
  }

  public void setAllows(List<AllowBlock> allow) {
    this.allows = allow;
  }

  public void setGroups(List<GroupBlock> group) {
    this.groups = group;
  }

  @Override
  public void setName(Ident<Class<? extends HasUuid>> name) {
    this.name = name;
  }

  public void setPolicies(List<PropertyPolicy> policy) {
    this.policies = policy;
  }

  public void setVerbs(List<ActionDefinition> verb) {
    this.verbs = verb;
  }
}