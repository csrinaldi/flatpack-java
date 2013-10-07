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
import com.getperka.flatpack.ext.PropertyPath;
import com.getperka.flatpack.policy.visitors.PolicyVisitor;
import com.getperka.flatpack.security.SecurityGroup;

/**
 * Defines a {@link SecurityGroup} and the {@link PropertyPath} that should be evaluated to
 * determine its memberships.
 */
public class GroupDefinition extends PolicyNode implements HasName<SecurityGroup> {
  private Ident<SecurityGroup> name;
  private List<Ident<PropertyPath>> paths = listForAny();

  public GroupDefinition() {}

  /**
   * Create an inherited group definition.
   */
  public GroupDefinition(GroupDefinition copyFrom, Ident<Property> prefix) {
    // Prefix the inherited name
    List<Ident<?>> newNameParts = listForAny();
    newNameParts.add(prefix);
    if (copyFrom.name.isSimple()) {
      newNameParts.add(copyFrom.name);
    } else {
      newNameParts.addAll(copyFrom.name.getCompoundName());
    }
    name = new Ident<SecurityGroup>(SecurityGroup.class, newNameParts);

    // Prepend the new Property onto the copied property paths
    for (Ident<PropertyPath> old : copyFrom.paths) {
      Ident<PropertyPath> path;

      // Either prepend a new path segment to a compound name, or turn a simple name into a compound
      if (old.isCompound()) {
        List<Ident<?>> newName = listForAny();
        newName.add(prefix);
        newName.addAll(old.getCompoundName());
        path = new Ident<PropertyPath>(PropertyPath.class, newName);
      } else {
        path = new Ident<PropertyPath>(PropertyPath.class, prefix,
            new Ident<Property>(Property.class, old.getSimpleName()));
      }

      paths.add(path);
    }
  }

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(name);
      v.traverse(paths);
    }
    v.endVisit(this);
  }

  @Override
  public Ident<SecurityGroup> getName() {
    return name;
  }

  public List<Ident<PropertyPath>> getPaths() {
    return paths;
  }

  @Override
  public void setName(Ident<SecurityGroup> name) {
    this.name = name;
  }

  public void setPaths(List<Ident<PropertyPath>> paths) {
    this.paths = paths;
  }
}