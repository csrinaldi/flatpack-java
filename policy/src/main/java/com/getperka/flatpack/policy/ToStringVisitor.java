package com.getperka.flatpack.policy;

import java.util.List;

/**
 * Elides most subnodes to produce output suitable for use in a debugger.
 */
public class ToStringVisitor extends ToSourceVisitor {

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
      super.traverse(x);
    } else {
      print(" ... ");
    }
  }

}
