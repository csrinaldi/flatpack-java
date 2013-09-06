package com.getperka.flatpack.policy.pst;

import java.util.List;

import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityGroup;

public class AclRule extends PolicyNode {
  private Ident<SecurityGroup> groupName;
  private List<Ident<SecurityAction>> securityActions = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(groupName);
      v.traverse(securityActions);
    }
    v.endVisit(this);
  }

  public Ident<SecurityGroup> getGroupName() {
    return groupName;
  }

  public List<Ident<SecurityAction>> getSecurityActions() {
    return securityActions;
  }

  public void setGroupName(Ident<SecurityGroup> groupName) {
    this.groupName = groupName;
  }

  public void setSecurityActions(List<Ident<SecurityAction>> verbNames) {
    this.securityActions = verbNames;
  }
}