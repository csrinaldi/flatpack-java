package com.getperka.flatpack.policy;

import java.util.List;

public class Group extends PolicyNode implements HasInheritFrom<Group> {
  private List<GroupDefinition> definitions = list();
  private Ident<Group> inheritFrom;

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(definitions);
      v.traverse(inheritFrom);
    }
    v.endVisit(this);
  }

  public List<GroupDefinition> getDefinitions() {
    return definitions;
  }

  @Override
  public Ident<Group> getInheritFrom() {
    return inheritFrom;
  }

  public void setDefinitions(List<GroupDefinition> definitions) {
    this.definitions = definitions;
  }

  @Override
  public void setInheritFrom(Ident<Group> inheritFrom) {
    this.inheritFrom = inheritFrom;
  }
}