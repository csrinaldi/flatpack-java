package com.getperka.flatpack.policy;

import java.util.Arrays;
import java.util.List;

/**
 * An ident is a (possibly-complex) name that ultimately refers to some in-memory object. To avoid
 * the need to have various {@code Map<String, Object>} scattered throughout the code, the
 * {@link #getReferent() referent} may be stored in the Ident itself. Idents are strongly-typed,
 * however the {@link #cast(Class)} method may be used to adjust the {@code R} parameter.
 * 
 * @param <R> the referent type
 */
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

  /**
   * Disallows cross-casting between unassignable types.
   */
  @SuppressWarnings("unchecked")
  public <T> Ident<T> cast(Class<T> clazz) {
    // Widening cast, just return the current ident
    if (clazz.isAssignableFrom(referentType)) {
      return (Ident<T>) this;
    }
    throw new ClassCastException("Ident of type " + referentType.getName() + " cannot be cast to "
      + clazz.getName());
  }

  /**
   * If the ident is a compound name (e.g. {@code foo.bar}), returns the component identifiers.
   */
  public List<Ident<Object>> getCompoundName() {
    if (compoundName == null) {
      throw new IllegalStateException("Not a compound identifier: " + this);
    }
    return compoundName;
  }

  public R getReferent() {
    return referent;
  }

  public Class<R> getReferentType() {
    return referentType;
  }

  public String getSimpleName() {
    if (simpleName == null) {
      throw new IllegalStateException("Not a simple identifier: " + this);
    }
    return simpleName;
  }

  public boolean isCompound() {
    return compoundName != null;
  }

  public boolean isReflexive() {
    return "this".equals(simpleName);
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