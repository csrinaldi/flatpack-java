package com.getperka.flatpack.visitors;

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
import java.util.ArrayDeque;
import java.util.Deque;

import javax.inject.Inject;

import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.codexes.EntityCodex;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.SerializationContext;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.inject.PackScoped;
import com.getperka.flatpack.security.CrudOperation;
import com.getperka.flatpack.security.Security;

/**
 * Performs an initial pass over the object graph to be serialized to populate the
 * SerializationContext.
 */
@PackScoped
public class PackScanner extends FlatPackVisitor {
  @Inject
  private SerializationContext context;
  @Inject
  private Security security;
  private Deque<HasUuid> stack = new ArrayDeque<HasUuid>();

  /**
   * Requires injection.
   */
  protected PackScanner() {}

  @Override
  public void endVisit(Property property, VisitorContext<Property> ctx) {
    context.popPath();
  }

  @Override
  public <T extends HasUuid> void endVisit(T entity, EntityCodex<T> codex, VisitorContext<T> ctx) {
    if (entity.equals(stack.peek())) {
      stack.pop();
      context.popPath();
    }
  }

  @Override
  public boolean visit(Property property, VisitorContext<Property> ctx) {
    context.pushPath("." + property.getName());
    if (!security.may(context.getPrincipal(), stack.peek(), property, CrudOperation.READ)) {
      return false;
    }
    if (property.isEmbedded()) {
      return false;
    }
    if (property.isDeepTraversalOnly() && !context.getTraversalMode().writeAllProperties()) {
      return false;
    }
    return true;
  }

  @Override
  public <T extends HasUuid> boolean visit(T entity, EntityCodex<T> codex, VisitorContext<T> ctx) {
    // TODO: EntitySecurity.mayRead() ?
    context.pushPath("." + entity.getUuid());
    stack.push(entity);
    return context.add(entity);
  }
}