package com.getperka.flatpack.policy;

import java.util.List;

import com.getperka.flatpack.ext.Property;

public class Group extends PolicyNode implements HasInheritFrom<Property> {
  private List<GroupDefinition> definitions = list();
  private Ident<Property> inheritFrom;

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
  public Ident<Property> getInheritFrom() {
    return inheritFrom;
  }

  public void setDefinitions(List<GroupDefinition> definitions) {
    this.definitions = definitions;
  }

  @Override
  public void setInheritFrom(Ident<Property> inheritFrom) {
    this.inheritFrom = inheritFrom;
  }
}