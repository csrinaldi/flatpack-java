package com.getperka.flatpack.policy;

import java.util.List;

public class PropertyList extends PolicyNode {
  private List<Ident<Object>> propertyNames = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(propertyNames);
    }
    v.endVisit(this);
  }

  public List<Ident<Object>> getPropertyNames() {
    return propertyNames;
  }

  public void setPropertyNames(List<Ident<Object>> propertyNames) {
    this.propertyNames = propertyNames;
  }
}