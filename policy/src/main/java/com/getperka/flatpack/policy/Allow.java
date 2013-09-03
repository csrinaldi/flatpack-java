package com.getperka.flatpack.policy;

import java.util.List;

import com.getperka.flatpack.ext.Property;

public class Allow extends PolicyNode implements HasInheritFrom<Property> {
  private List<AclRule> aclRules = list();
  private Ident<Property> inheritFrom;

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(aclRules);
      v.traverse(inheritFrom);
    }
    v.endVisit(this);
  }

  public List<AclRule> getAclRules() {
    return aclRules;
  }

  @Override
  public Ident<Property> getInheritFrom() {
    return inheritFrom;
  }

  public void setAclRules(List<AclRule> aclRules) {
    this.aclRules = aclRules;
  }

  @Override
  public void setInheritFrom(Ident<Property> inheritFrom) {
    this.inheritFrom = inheritFrom;
  }
}