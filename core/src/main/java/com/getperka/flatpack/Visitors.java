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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.ext.Walker;
import com.getperka.flatpack.ext.Walkers;

/**
 * A utility class that allows visitors to be written to traverse a FlatPack object graph.
 */
@Singleton
public class Visitors {
  class FlatPackEntityWalker<T> implements Walker<FlatPackEntity<T>> {
    @Override
    public void walk(FlatPackVisitor visitor, FlatPackEntity<T> entity,
        VisitorContext<FlatPackEntity<T>> context) {
      @SuppressWarnings("unchecked")
      Codex<T> codex = (Codex<T>) typeContext.getCodex(entity.getType());
      if (visitor.visit(entity, codex, context)) {
        entity.withValue(context.walkSingleton(codex).accept(visitor, entity.getValue()));

        // Traverse the extra entities as a list, to allow insertion
        Codex<HasUuid> extraCodex = typeContext.getCodex(HasUuid.class);
        List<HasUuid> mutable = new ArrayList<HasUuid>(entity.getExtraEntities());
        context.walkList(extraCodex).accept(visitor, mutable);
        entity.setExtraEntities(new HashSet<HasUuid>(mutable));
      }
      visitor.endVisit(entity, codex, context);
    }
  }

  @Inject
  TypeContext typeContext;

  @Inject
  Walkers walkers;

  protected Visitors() {}

  /**
   * Returns a {@link VisitorContext} for performing ad-hoc traversals.
   */
  public Walkers getWalkers() {
    return walkers;
  }

  /**
   * Visit a {@link FlatPackEntity}, including its {@link FlatPackEntity#getValue() value} and
   * {@link FlatPackEntity#getExtraEntities() extra entities};
   * 
   * @param visitor the visitor to receive the object graph
   * @param entity the FlatPackEntity to traverse
   * @return {@code entity} or its replacement
   */
  public <T> FlatPackEntity<T> visit(FlatPackVisitor visitor, FlatPackEntity<T> entity) {
    return getWalkers().walkSingleton(new FlatPackEntityWalker<T>()).accept(visitor, entity);
  }

  /**
   * Visit an entity.
   * 
   * @param visitor the visitor to receive the object graph
   * @param entity the entity to traverse
   * @return {@code entity} or its replacement
   */
  public <T extends HasUuid> T visit(FlatPackVisitor visitor, T entity) {
    @SuppressWarnings("unchecked")
    Class<T> clazz = (Class<T>) entity.getClass();
    Codex<T> codex = typeContext.getCodex(clazz);
    return getWalkers().walkSingleton(codex).accept(visitor, entity);
  }
}
