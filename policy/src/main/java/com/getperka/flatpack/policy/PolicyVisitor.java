package com.getperka.flatpack.policy;

import java.util.List;

public class PolicyVisitor {
  public void endVisit(AclRule x) {}

  public void endVisit(Allow x) {}

  public void endVisit(Group x) {}

  public void endVisit(GroupDefinition x) {}

  public void endVisit(Ident<?> x) {}

  public void endVisit(PolicyFile x) {}

  public void endVisit(PropertyList x) {}

  public void endVisit(PropertyPath x) {}

  public void endVisit(PropertyPolicy x) {}

  public void endVisit(TypePolicy x) {}

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

  public boolean visit(Ident<?> x) {
    return true;
  }

  public boolean visit(PolicyFile x) {
    return true;
  }

  public boolean visit(PropertyList x) {
    return true;
  }

  public boolean visit(PropertyPath x) {
    return true;
  }

  public boolean visit(PropertyPolicy x) {
    return true;
  }

  public boolean visit(TypePolicy x) {
    return true;
  }

  public boolean visit(Verb x) {
    return true;
  }

  protected void traverse(List<? extends PolicyNode> list) {
    if (list == null) {
      return;
    }
    for (PolicyNode x : list) {
      traverse(x);
    }
  }

  protected void traverse(PolicyNode x) {
    if (x != null) {
      x.accept(this);
    }
  }
}