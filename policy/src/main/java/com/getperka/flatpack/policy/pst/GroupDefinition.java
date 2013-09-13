package com.getperka.flatpack.policy.pst;

import java.util.List;

import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.PropertyPath;
import com.getperka.flatpack.ext.SecurityGroup;

public class GroupDefinition extends PolicyNode implements HasName<SecurityGroup> {
  private Ident<SecurityGroup> name;
  private List<Ident<PropertyPath>> paths = list();

  public GroupDefinition() {}

  /**
   * Create an inherited group definition.
   */
  public GroupDefinition(GroupDefinition copyFrom, Ident<Property> prefix) {
    // Prefix the inherited name
    List<Ident<?>> newNameParts = list();
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
        List<Ident<?>> newName = list();
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