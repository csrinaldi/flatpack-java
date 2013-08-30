package com.getperka.flatpack.policy;

import java.util.List;

import com.getperka.flatpack.ext.PropertyPath;

public class GroupDefinition extends PolicyNode implements HasName<GroupDefinition> {
  private Ident<GroupDefinition> name;
  private List<Ident<PropertyPath>> paths = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(name);
      v.traverse(paths);
    }
    v.endVisit(this);
  }

  @Override
  public Ident<GroupDefinition> getName() {
    return name;
  }

  public List<Ident<PropertyPath>> getPaths() {
    return paths;
  }

  @Override
  public void setName(Ident<GroupDefinition> name) {
    this.name = name;
  }

  public void setPaths(List<Ident<PropertyPath>> paths) {
    this.paths = paths;
  }
}