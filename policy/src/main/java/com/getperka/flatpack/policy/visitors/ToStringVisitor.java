package com.getperka.flatpack.policy.visitors;

import java.util.List;

import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PolicyNode;

/**
 * Elides most subnodes to produce output suitable for use in a debugger.
 */
public class ToStringVisitor extends ToSourceVisitor {

  @Override
  public boolean visit(Ident<?> x) {
    // Always print compound names
    if (x.isCompound()) {
      super.traverse(x.getCompoundName(), ".");
      return false;
    }
    return super.visit(x);
  }

  @Override
  protected void nl() {}

  @Override
  protected void printBlockOrSingleton(List<? extends PolicyNode> list) {
    print(" { " + list.size() + " nodes } ");
  }

  @Override
  protected void traverse(List<? extends PolicyNode> list) {
    print(" ... ");
  }

  @Override
  protected void traverse(List<? extends PolicyNode> list, String separator) {
    print(" ... ");
  }

  @Override
  protected void traverse(PolicyNode x) {
    // Always print single idents, since they make the summary usable
    if (x instanceof Ident) {
      print(x.toSource());
    } else {
      print(" ... ");
    }
  }

}
