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

import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.policy.visitors.PolicyVisitor;

/**
 * Associates one or more {@link Property} instances with an {@link AllowBlock}.
 */
public class PropertyPolicy extends PolicyNode implements HasName<PropertyPolicy> {
  private List<AllowBlock> allows = listForAny();
  private Ident<PropertyPolicy> name;
  private List<PropertyList> propertyLists = listForAny();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(allows);
      v.traverse(name);
      v.traverse(propertyLists);
    }
    v.endVisit(this);
  }

  public List<AllowBlock> getAllows() {
    return allows;
  }

  @Override
  public Ident<PropertyPolicy> getName() {
    return name;
  }

  public List<PropertyList> getPropertyLists() {
    return propertyLists;
  }

  public void setAllows(List<AllowBlock> allows) {
    this.allows = allows;
  }

  @Override
  public void setName(Ident<PropertyPolicy> name) {
    this.name = name;
  }

  public void setPropertyLists(List<PropertyList> propertyLists) {
    this.propertyLists = propertyLists;
  }
}