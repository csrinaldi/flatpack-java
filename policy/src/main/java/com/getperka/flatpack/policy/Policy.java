package com.getperka.flatpack.policy;

import java.util.List;

public class Policy extends PolicyNode implements HasInheritFrom<Policy>, HasName<Policy> {
  private List<Allow> allows = list();
  private Ident<Policy> inheritFrom;
  private Ident<Policy> name;
  private List<PropertyList> propertyLists = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(allows);
      v.traverse(propertyLists);
    }
    v.endVisit(this);
  }

  public List<Allow> getAllows() {
    return allows;
  }

  @Override
  public Ident<Policy> getInheritFrom() {
    return inheritFrom;
  }

  @Override
  public Ident<Policy> getName() {
    return name;
  }

  public List<PropertyList> getPropertyLists() {
    return propertyLists;
  }

  public void setAllows(List<Allow> allows) {
    this.allows = allows;
  }

  @Override
  public void setInheritFrom(Ident<Policy> inheritFrom) {
    this.inheritFrom = inheritFrom;
  }

  @Override
  public void setName(Ident<Policy> name) {
    this.name = name;
  }

  public void setPropertyLists(List<PropertyList> propertyLists) {
    this.propertyLists = propertyLists;
  }
}