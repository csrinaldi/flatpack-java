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

/**
 * A base type for nodes that should contain general policy data.
 */
public abstract class PolicyBlock extends PolicyNode {

  private List<AllowBlock> allows = listForAny();
  private List<ActionDefinition> verbs = listForAny();
  private List<PackagePolicy> packagePolicies = listForAny();
  private List<TypePolicy> typePolicies = listForAny();

  public List<AllowBlock> getAllows() {
    return allows;
  }

  public List<PackagePolicy> getPackagePolicies() {
    return packagePolicies;
  }

  public List<TypePolicy> getTypePolicies() {
    return typePolicies;
  }

  public List<ActionDefinition> getVerbs() {
    return verbs;
  }

  public void setAllows(List<AllowBlock> allows) {
    this.allows = allows;
  }

  public void setPackagePolicies(List<PackagePolicy> packagePolicies) {
    this.packagePolicies = packagePolicies;
  }

  public void setTypePolicies(List<TypePolicy> types) {
    this.typePolicies = types;
  }

  public void setVerbs(List<ActionDefinition> verbs) {
    this.verbs = verbs;
  }

}