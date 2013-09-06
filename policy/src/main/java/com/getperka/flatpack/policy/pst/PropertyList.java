package com.getperka.flatpack.policy.pst;

import java.util.List;

import com.getperka.flatpack.ext.Property;

public class PropertyList extends PolicyNode {
  private List<Ident<Property>> propertyNames = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(propertyNames);
    }
    v.endVisit(this);
  }

  public List<Ident<Property>> getPropertyNames() {
    return propertyNames;
  }

  public void setPropertyNames(List<Ident<Property>> propertyNames) {
    this.propertyNames = propertyNames;
  }
}