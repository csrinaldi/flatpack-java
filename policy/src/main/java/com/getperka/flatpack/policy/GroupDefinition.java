package com.getperka.flatpack.policy;

import java.util.List;

import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.PropertyPath;
import com.getperka.flatpack.ext.SecurityGroup;

public class GroupDefinition extends PolicyNode implements HasName<SecurityGroup> {
  private Ident<SecurityGroup> name;
  private List<Ident<PropertyPath>> paths = list();

  public GroupDefinition() {}

  public GroupDefinition(GroupDefinition copyFrom, Property prefix) {
    this.name = copyFrom.name;

    // Prepend the new Property onto the copied property paths
    Ident<Property> prefixIdent = new Ident<Property>(Property.class, prefix.getName());
    prefixIdent.setReferent(prefix);
    for (Ident<PropertyPath> old : copyFrom.paths) {
      Ident<PropertyPath> path;

      // Either prepend a new path segment to a compound name, or turn a simple name into a compound
      if (old.isCompound()) {
        path = new Ident<PropertyPath>(PropertyPath.class, old.getCompoundName());
        path.getCompoundName().add(0, prefixIdent);
      } else {
        path = new Ident<PropertyPath>(PropertyPath.class, prefixIdent,
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