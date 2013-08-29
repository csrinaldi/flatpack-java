package com.getperka.flatpack.policy;

import java.util.Arrays;
import java.util.List;

public class Ident<R> extends PolicyNode {
  private final List<Ident<Object>> compoundName;
  private final String simpleName;
  private R referent;

  public Ident(Ident<Object>... compoundName) {
    this(Arrays.asList(compoundName));
  }

  public Ident(List<Ident<Object>> compoundName) {
    this.compoundName = compoundName;
    this.simpleName = null;
  }

  public Ident(String simpleName) {
    this.compoundName = null;
    this.simpleName = simpleName;
  }

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(compoundName);
    }
    v.endVisit(this);
  }

  public List<Ident<Object>> getCompoundName() {
    return compoundName;
  }

  public R getReferent() {
    return referent;
  }

  public String getSimpleName() {
    return simpleName;
  }

  public boolean isCompound() {
    return compoundName != null;
  }

  public boolean isSimple() {
    return simpleName != null;
  }

  public boolean isWildcard() {
    return "*".equals(simpleName);
  }

  public void setReferent(R referent) {
    this.referent = referent;
  }
}