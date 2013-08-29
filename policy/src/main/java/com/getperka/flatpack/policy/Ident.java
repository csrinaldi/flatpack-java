package com.getperka.flatpack.policy;

import java.util.Arrays;
import java.util.List;

public class Ident<R> {
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

  @Override
  public String toString() {
    if (isSimple()) {
      return getSimpleName();
    } else if (isCompound()) {
      StringBuilder sb = new StringBuilder();
      boolean needsDot = false;
      for (Ident<?> ident : compoundName) {
        if (needsDot) {
          sb.append(".");
        } else {
          needsDot = true;
        }
        sb.append(ident.toString());
      }
      return sb.toString();
    } else {
      return "<NULL>";
    }
  }
}