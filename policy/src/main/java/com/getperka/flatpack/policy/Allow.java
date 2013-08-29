package com.getperka.flatpack.policy;

import java.util.List;

public class Allow extends PolicyNode implements HasInheritFrom<Allow> {
  private List<AclRule> aclRules = list();
  private Ident<Allow> inheritFrom;

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
  public Ident<Allow> getInheritFrom() {
    return inheritFrom;
  }

  public void setAclRules(List<AclRule> aclRules) {
    this.aclRules = aclRules;
  }

  @Override
  public void setInheritFrom(Ident<Allow> inheritFrom) {
    this.inheritFrom = inheritFrom;
  }
}