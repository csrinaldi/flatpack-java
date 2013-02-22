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

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.ext.VisitorContext.IterableContext;
import com.getperka.flatpack.ext.VisitorContext.SingletonContext;

/**
 * A utility class that allows visitors to be written to traverse a FlatPack object graph.
 */
public class Visitors {

  @Inject
  private TypeContext typeContext;

  protected Visitors() {}

  public <T> FlatPackEntity<T> visit(FlatPackVisitor visitor, FlatPackEntity<T> entity) {
    SingletonContext<FlatPackEntity<T>> ctx = new SingletonContext<FlatPackEntity<T>>();
    if (visitor.visit(entity, ctx)) {
      if (entity.getValue() != null) {
        @SuppressWarnings("unchecked")
        Codex<T> codex = (Codex<T>) typeContext.getCodex(entity.getType());

        SingletonContext<T> valueContext = new SingletonContext<T>();
        valueContext.acceptSingleton(visitor, entity.getValue(), codex);
        if (valueContext.didReplace()) {
          entity.withValue(valueContext.getValue());
        }
      }
      Codex<HasUuid> extraCodex = typeContext.getCodex(HasUuid.class);

      Set<HasUuid> mutable = new HashSet<HasUuid>(entity.getExtraEntities());
      new IterableContext<HasUuid>().acceptIterable(visitor,
          mutable, extraCodex);
      entity.setExtraEntities(mutable);
    }
    visitor.endVisit(entity, ctx);
    if (ctx.didReplace()) {
      entity = ctx.getValue();
    }
    return entity;
  }

  public <T extends HasUuid> T visit(FlatPackVisitor visitor, T entity) {
    @SuppressWarnings("unchecked")
    Codex<T> codex = (Codex<T>) typeContext.getCodex(entity.getClass());
    SingletonContext<T> ctx = new SingletonContext<T>();
    ctx.acceptSingleton(visitor, entity, codex);
    return ctx.didReplace() ? ctx.getValue() : entity;
  }
}
