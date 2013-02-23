package com.getperka.flatpack.ext;


/*
 * #%L
 * FlatPack serialization code
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

/**
 * Allows mutations to the object graph during traversal.
 * <p>
 * The available mutations are dependent upon the value being visited. For example, an element in a
 * list can be replaced, removed, or have siblings added, whereas insertion doesn't make sense for a
 * simple scalar property. For that reason, the various {@code can} methods should be used to guard
 * calls to the mutators.
 * <p>
 * Various container-specific implementations of {@link VisitorContext} are available for use when
 * writing custom {@link Walker} implementations. Each context implementation has an {@code accept}
 * method which handles traversing and mutating some particular type of container object.
 * 
 * @param <T> the type of data currently being visited
 */
public class VisitorContext<T> extends Walkers {
  private boolean inserted;
  private boolean removed;
  private boolean replaced;
  private Walker<T> walker;

  protected VisitorContext() {}

  public boolean canInsert() {
    return false;
  }

  public boolean canRemove() {
    return false;
  }

  public boolean canReplace() {
    return false;
  }

  public boolean didInsert() {
    return inserted;
  }

  public boolean didRemove() {
    return removed;
  }

  public boolean didReplace() {
    return replaced;
  }

  public void insertAfter(T newValue) {
    ensure(false);
  }

  public void insertBefore(T newValue) {
    ensure(false);
  }

  public void remove() {
    ensure(false);
  }

  public void replace(T newValue) {
    ensure(false);
  }

  protected void ensure(boolean capability) {
    if (!capability) {
      throw new UnsupportedOperationException();
    }
  }

  protected Walker<T> getWalker() {
    return walker;
  }

  protected void markInserted() {
    inserted = true;
  }

  protected void markRemoved() {
    removed = true;
    replaced = false;
  }

  protected void markReplaced() {
    removed = false;
    replaced = true;
  }

  protected void setWalker(Walker<T> walker) {
    this.walker = walker;
  }
}