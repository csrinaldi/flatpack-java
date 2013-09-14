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

/**
 * A base type for nodes that should contain general policy data.
 */
public abstract class PolicyBlock extends PolicyNode {

  private List<Allow> allows = list();
  private List<Verb> verbs = list();
  private List<PackagePolicy> packagePolicies = list();
  private List<TypePolicy> typePolicies = list();

  public List<Allow> getAllows() {
    return allows;
  }

  public List<PackagePolicy> getPackagePolicies() {
    return packagePolicies;
  }

  public List<TypePolicy> getTypePolicies() {
    return typePolicies;
  }

  public List<Verb> getVerbs() {
    return verbs;
  }

  public void setAllows(List<Allow> allows) {
    this.allows = allows;
  }

  public void setPackagePolicies(List<PackagePolicy> packagePolicies) {
    this.packagePolicies = packagePolicies;
  }

  public void setTypePolicies(List<TypePolicy> types) {
    this.typePolicies = types;
  }

  public void setVerbs(List<Verb> verbs) {
    this.verbs = verbs;
  }

}