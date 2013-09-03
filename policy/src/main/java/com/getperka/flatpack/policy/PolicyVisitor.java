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

  public void endVisit(PropertyPolicy x) {}

  public void endVisit(TypePolicy x) {}

  public void endVisit(Verb x) {}

  public boolean visit(AclRule x) {
    return defaultVisit();
  }

  public boolean visit(Allow x) {
    return defaultVisit();
  }

  public boolean visit(Group x) {
    return defaultVisit();
  }

  public boolean visit(GroupDefinition x) {
    return defaultVisit();
  }

  public boolean visit(Ident<?> x) {
    return defaultVisit();
  }

  public boolean visit(PolicyFile x) {
    return defaultVisit();
  }

  public boolean visit(PropertyList x) {
    return defaultVisit();
  }

  public boolean visit(PropertyPolicy x) {
    return defaultVisit();
  }

  public boolean visit(TypePolicy x) {
    return defaultVisit();
  }

  public boolean visit(Verb x) {
    return defaultVisit();
  }

  protected boolean defaultVisit() {
    return true;
  }

  /**
   * Traverse a list of nodes. This method is null-safe.
   */
  protected void traverse(List<? extends PolicyNode> list) {
    if (list == null) {
      return;
    }
    for (PolicyNode x : list) {
      traverse(x);
    }
  }

  /**
   * Traverse a single node. This method should be called in preference to calling
   * {@link PolicyNode#accept(PolicyVisitor)} directly, since subclasses of PolicyVisitor may wish
   * to influence the traversal logic. It is also null-safe.
   */
  protected void traverse(PolicyNode x) {
    if (x != null) {
      x.accept(this);
    }
  }
}