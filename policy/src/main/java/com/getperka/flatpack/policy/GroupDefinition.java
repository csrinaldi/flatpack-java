package com.getperka.flatpack.policy;

import java.util.List;

public class GroupDefinition extends PolicyNode implements HasName<GroupDefinition> {
  private Ident<GroupDefinition> name;
  private List<PropertyPath> paths = list();

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

  public List<PropertyPath> getPaths() {
    return paths;
  }

  @Override
  public void setName(Ident<GroupDefinition> name) {
    this.name = name;
  }

  public void setPaths(List<PropertyPath> paths) {
    this.paths = paths;
  }
}