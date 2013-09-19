package com.getperka.flatpack.policy.pst;

/*
 * #%L
 * FlatPack Security Policy
 * %%
 * Copyright (C) 2012 - 2013 Perka Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

/**
 * An ident is a (possibly-complex) name that ultimately refers to some in-memory object. To avoid
 * the need to have various {@code Map<String, Object>} scattered throughout the code, the
 * {@link #getReferent() referent} may be stored in the Ident itself. Idents are strongly-typed,
 * however the {@link #cast(Class)} method may be used to adjust the {@code R} parameter.
 * 
 * @param <R> the referent type
 */
public class Ident<R> extends PolicyNode {
  private final List<Ident<?>> compoundName;
  private final int hashCode;
  private R referent;
  private final Class<R> referentType;
  private final String simpleName;

  public Ident(Class<R> type, Ident<?>... compoundName) {
    this(type, Arrays.asList(compoundName));
  }

  public Ident(Class<R> type, List<Ident<?>> compoundName) {
    for (Ident<?> sub : compoundName) {
      if (sub.isCompound()) {
        throw new IllegalArgumentException("Compound Idents can only be composed of simple Idents");
      }
    }

    this.compoundName = Collections.unmodifiableList(listForAny(compoundName));
    this.simpleName = null;
    this.referentType = type;

    this.hashCode = this.referentType.hashCode() * 3 + this.compoundName.hashCode() * 5;
  }

  public Ident(Class<R> type, String simpleName) {
    this.compoundName = null;
    this.simpleName = simpleName;
    this.referentType = type;
    this.hashCode = type.hashCode() * 3 + simpleName.hashCode() * 7;
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
    if (referentType.isAssignableFrom(clazz)) {
      return (Ident<T>) this;
    }
    throw new ClassCastException("Ident of type " + referentType.getName() + " cannot be cast to "
      + clazz.getName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Ident)) {
      return false;
    }
    Ident<?> other = (Ident<?>) o;
    if (!referentType.equals(other.referentType)) {
      return false;
    }
    if (simpleName != null) {
      return simpleName.equals(other.simpleName);
    }
    if (compoundName != null) {
      return compoundName.equals(other.compoundName);
    }
    throw new UnsupportedOperationException();
  }

  /**
   * If the ident is a compound name (e.g. {@code foo.bar}), returns the component identifiers.
   */
  public List<Ident<?>> getCompoundName() {
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

  @Override
  public int hashCode() {
    return hashCode;
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

  /**
   * Given a compound ident {@code a.b.c} returns {@code b.c}. The newly-returned Ident, and it's
   * compound Idents, will be distinct copies.
   * 
   * @throws IllegalArgumentException if the current ident is simple
   */
  public Ident<R> removeLeadingIdent() {
    if (isSimple()) {
      throw new IllegalArgumentException();
    }
    // Convert to a simple ident
    if (compoundName.size() == 2) {
      return new Ident<R>(getReferentType(), compoundName.get(1).getSimpleName());
    }
    List<Ident<?>> newName = listForAny(compoundName);
    newName.remove(0);
    for (ListIterator<Ident<?>> it = newName.listIterator(); it.hasNext();) {
      Ident<?> toCopy = it.next();
      @SuppressWarnings({ "rawtypes", "unchecked" })
      Ident<?> copy = new Ident(toCopy.getReferentType(), toCopy.getSimpleName());
      it.set(copy);
    }
    return new Ident<R>(getReferentType(), newName);
  }

  public void setReferent(R referent) {
    this.referent = referentType.cast(referent);
  }
}