package com.getperka.flatpack.policy;

import java.util.Arrays;
import java.util.List;

public class Ident<R> extends PolicyNode {
  private final List<Ident<Object>> compoundName;
  private R referent;
  private final Class<R> referentType;
  private final String simpleName;

  public Ident(Class<R> type, Ident<Object>... compoundName) {
    this(type, Arrays.asList(compoundName));
  }

  public Ident(Class<R> type, List<Ident<Object>> compoundName) {
    this.compoundName = compoundName;
    this.simpleName = null;
    this.referentType = type;
  }

  public Ident(Class<R> type, String simpleName) {
    this.compoundName = null;
    this.simpleName = simpleName;
    this.referentType = type;
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

  public Class<R> getReferentType() {
    return referentType;
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
    this.referent = referentType.cast(referent);
  }
}