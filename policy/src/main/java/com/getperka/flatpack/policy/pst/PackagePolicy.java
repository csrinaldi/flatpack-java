package com.getperka.flatpack.policy.pst;

import com.getperka.flatpack.policy.visitors.PolicyVisitor;
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

/**
 * Allows types and associated declarations to be grouped together.
 */
public class PackagePolicy extends PolicyBlock implements HasName<PackagePolicy> {
  private Ident<PackagePolicy> name;

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(getAllows());
      v.traverse(getPackagePolicies());
      v.traverse(getName());
      v.traverse(getTypePolicies());
      v.traverse(getVerbs());
    }
    v.endVisit(this);
  }

  @Override
  public Ident<PackagePolicy> getName() {
    return name;
  }

  @Override
  public void setName(Ident<PackagePolicy> name) {
    this.name = name;
  }
}
