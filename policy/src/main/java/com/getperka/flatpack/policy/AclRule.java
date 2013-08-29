package com.getperka.flatpack.policy;

import java.util.List;

public class AclRule extends PolicyNode {
  private Ident<Group> groupName;
  private List<Ident<Verb>> verbNames = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      // No sub-nodes
    }
    v.endVisit(this);
  }

  public Ident<Group> getGroupName() {
    return groupName;
  }

  public List<Ident<Verb>> getVerbNames() {
    return verbNames;
  }

  public void setGroupName(Ident<Group> groupName) {
    this.groupName = groupName;
  }

  public void setVerbNames(List<Ident<Verb>> verbNames) {
    this.verbNames = verbNames;
  }
}