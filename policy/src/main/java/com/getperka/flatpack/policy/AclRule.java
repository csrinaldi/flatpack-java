package com.getperka.flatpack.policy;

import java.util.List;

import com.getperka.flatpack.ext.SecurityGroup;

public class AclRule extends PolicyNode {
  private Ident<SecurityGroup> groupName;
  private List<Ident<VerbAction>> verbActions = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(groupName);
      v.traverse(verbActions);
    }
    v.endVisit(this);
  }

  public Ident<SecurityGroup> getGroupName() {
    return groupName;
  }

  public List<Ident<VerbAction>> getVerbActions() {
    return verbActions;
  }

  public void setGroupName(Ident<SecurityGroup> groupName) {
    this.groupName = groupName;
  }

  public void setVerbActions(List<Ident<VerbAction>> verbNames) {
    this.verbActions = verbNames;
  }
}