package com.getperka.flatpack.policy;

import java.util.List;

public class PropertyPath extends PolicyNode {
  private List<Ident<Object>> pathParts = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(pathParts);
    }
    v.endVisit(this);
  }

  public List<Ident<Object>> getPathParts() {
    return pathParts;
  }

  public void setPathParts(List<Ident<Object>> pathParts) {
    this.pathParts = pathParts;
  }
}