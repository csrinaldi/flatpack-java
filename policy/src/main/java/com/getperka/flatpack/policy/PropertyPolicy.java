package com.getperka.flatpack.policy;

import java.util.List;

public class PropertyPolicy extends PolicyNode implements HasInheritFrom<PropertyPolicy>,
    HasName<PropertyPolicy> {
  private List<Allow> allows = list();
  private Ident<PropertyPolicy> inheritFrom;
  private Ident<PropertyPolicy> name;
  private List<PropertyList> propertyLists = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(allows);
      v.traverse(inheritFrom);
      v.traverse(name);
      v.traverse(propertyLists);
    }
    v.endVisit(this);
  }

  public List<Allow> getAllows() {
    return allows;
  }

  @Override
  public Ident<PropertyPolicy> getInheritFrom() {
    return inheritFrom;
  }

  @Override
  public Ident<PropertyPolicy> getName() {
    return name;
  }

  public List<PropertyList> getPropertyLists() {
    return propertyLists;
  }

  public void setAllows(List<Allow> allows) {
    this.allows = allows;
  }

  @Override
  public void setInheritFrom(Ident<PropertyPolicy> inheritFrom) {
    this.inheritFrom = inheritFrom;
  }

  @Override
  public void setName(Ident<PropertyPolicy> name) {
    this.name = name;
  }

  public void setPropertyLists(List<PropertyList> propertyLists) {
    this.propertyLists = propertyLists;
  }
}