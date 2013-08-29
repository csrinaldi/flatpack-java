package com.getperka.flatpack.policy;

import java.util.List;

public class PolicyVisitor {
  public void endVisit(AclRule x) {}

  public void endVisit(Allow x) {}

  public void endVisit(Group x) {}

  public void endVisit(GroupDefinition x) {}

  public void endVisit(Policy x) {}

  public void endVisit(PolicyFile x) {}

  public void endVisit(PolicyNode x) {}

  public void endVisit(PropertyList x) {}

  public void endVisit(PropertyPath x) {}

  public void endVisit(Type x) {}

  public void endVisit(Verb x) {}

  public boolean visit(AclRule x) {
    return true;
  }

  public boolean visit(Allow x) {
    return true;
  }

  public boolean visit(Group x) {
    return true;
  }

  public boolean visit(GroupDefinition x) {
    return true;
  }

  public boolean visit(Policy x) {
    return true;
  }

  public boolean visit(PolicyFile x) {
    return true;
  }

  public boolean visit(PolicyNode x) {
    return true;
  }

  public boolean visit(PropertyList x) {
    return true;
  }

  public boolean visit(PropertyPath x) {
    return true;
  }

  public boolean visit(Type x) {
    return true;
  }

  public boolean visit(Verb x) {
    return true;
  }

  protected void traverse(List<? extends PolicyNode> list) {
    for (PolicyNode x : list) {
      x.accept(this);
    }
  }

  protected void traverse(PolicyNode x) {
    x.accept(this);
  }
}