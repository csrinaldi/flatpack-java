package com.getperka.flatpack;

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

import com.getperka.flatpack.codexes.EntityCodex;
import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.util.AcyclicVisitor;

/**
 * The base type for visitors that can traverse a FlatPack-compatible object graph.
 * <p>
 * The {@code visit} methods are called when encountering an interesting feature of the graph. If
 * they return {@code true}, the details of that feature will also be visited. Regardless of the
 * return value from {@code visit}, the associated {@code endVisit} will always be called.
 * <p>
 * In general, the visitation pattern looks like
 * 
 * <pre>
 * FlatPackEntity
 *   Value
 *     Entity
 *       Property
 *         Value (May be null)
 *           [ Entity ... ]
 *       Property
 *         Value
 *           [ Entity ... ]
 *       [ Property ... ]
 *   ExtraEntities
 *     [ Entity ... ]
 * </pre>
 * 
 * For entity references, both {@link #visitValue(Object, Codex, VisitorContext) visitValue()} and
 * {@link #visit(HasUuid, EntityCodex, VisitorContext) visit(HasUuid)} will be called. Unset
 * properties will call {@link #visitValue(Object, Codex, VisitorContext) visitValue()} with a
 * {@code null} value.
 * <p>
 * Mutating the object graph is accomplished via {@link VisitorContext}. In general, calling the
 * mutator methods on the context object will not alter the visitor's traversal of the entity or
 * value. The effects of modifying an object graph that is being traversed is undefined. Custom
 * {@link Codex} subtypes may introduce additional capability interfaces to enhance traversal of
 * non-trivial datastructures.
 * <p>
 * Cycle-detection is not implemented in the base class.
 * 
 * @see AcyclicVisitor
 */
public class FlatPackVisitor {
  public <T> void endVisit(FlatPackEntity<T> x, Codex<T> codex,
      VisitorContext<FlatPackEntity<T>> ctx) {}

  public void endVisit(Property property, VisitorContext<Property> ctx) {}

  public <T extends HasUuid> void endVisit(T entity, EntityCodex<T> codex, VisitorContext<T> ctx) {}

  public <T> void endVisitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {}

  public <T> boolean visit(FlatPackEntity<T> x, Codex<T> codex,
      VisitorContext<FlatPackEntity<T>> ctx) {
    return isGreedy();
  }

  public boolean visit(Property property, VisitorContext<Property> ctx) {
    return isGreedy();
  }

  public <T extends HasUuid> boolean visit(T entity, EntityCodex<T> codex, VisitorContext<T> ctx) {
    return isGreedy();
  }

  public <T> boolean visitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
    return isGreedy();
  }

  /**
   * Returns the default return value for the {@code visit} methods. Subclasses may override this
   * method to control whether or not the visitor will greedily traverse the object graph.
   * <p>
   * The default value is {@code true}.
   */
  protected boolean isGreedy() {
    return true;
  }
}
