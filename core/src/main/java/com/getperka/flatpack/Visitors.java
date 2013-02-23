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
import com.getperka.flatpack.ext.VisitorContext.ListContext;
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
    @SuppressWarnings("unchecked")
    Codex<T> codex = (Codex<T>) typeContext.getCodex(entity.getType());
    if (visitor.visit(entity, codex, ctx)) {
      if (entity.getValue() != null) {
        SingletonContext<T> valueContext = new SingletonContext<T>();
        valueContext.acceptSingleton(visitor, entity.getValue(), codex);
        if (valueContext.didReplace()) {
          entity.withValue(valueContext.getValue());
        }
      }
      Codex<HasUuid> extraCodex = typeContext.getCodex(HasUuid.class);

      // Traverse the extra entities as a list, to allow insertion
      List<HasUuid> mutable = new ArrayList<HasUuid>(entity.getExtraEntities());
      new ListContext<HasUuid>().acceptList(visitor, mutable, extraCodex);
      entity.setExtraEntities(new HashSet<HasUuid>(mutable));
    }
    visitor.endVisit(entity, codex, ctx);
    if (ctx.didReplace()) {
      entity = ctx.getValue();
    }
    return entity;
  }

  public <T extends HasUuid> T visit(FlatPackVisitor visitor, T entity) {
    @SuppressWarnings("unchecked")
    Class<T> clazz = (Class<T>) entity.getClass();
    Codex<T> codex = typeContext.getCodex(clazz);
    SingletonContext<T> ctx = new SingletonContext<T>();
    ctx.acceptSingleton(visitor, entity, codex);
    return ctx.didReplace() ? ctx.getValue() : entity;
  }
}
