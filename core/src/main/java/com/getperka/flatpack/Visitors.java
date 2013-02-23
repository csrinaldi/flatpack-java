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

import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.ext.VisitorContext.Walker;

/**
 * A utility class that allows visitors to be written to traverse a FlatPack object graph.
 */
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
  VisitorContext<Void> rootContext;

  protected Visitors() {}

  public VisitorContext<Void> getRoot() {
    return rootContext;
  }

  public <T> FlatPackEntity<T> visit(FlatPackVisitor visitor, FlatPackEntity<T> entity) {
    return getRoot().walkSingleton(new FlatPackEntityWalker<T>()).accept(visitor, entity);
  }

  public <T extends HasUuid> T visit(FlatPackVisitor visitor, T entity) {
    @SuppressWarnings("unchecked")
    Class<T> clazz = (Class<T>) entity.getClass();
    Codex<T> codex = typeContext.getCodex(clazz);
    return getRoot().walkSingleton(codex).accept(visitor, entity);
  }
}
