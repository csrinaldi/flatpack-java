package com.getperka.flatpack.util;
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

import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.codexes.EntityCodex;
import com.getperka.flatpack.ext.VisitorContext;

/**
 * A {@link FlatPackVisitor} that breaks graph cycles by recording which entities have been
 * traversed.
 */
public class AcyclicVisitor extends FlatPackVisitor {
  private final Set<HasUuid> ended = new HashSet<HasUuid>();
  private final Set<HasUuid> visited = new HashSet<HasUuid>();

  /**
   * Delegates to {@link #endVisitOnce} if {@code entity} has not been visited previously.
   */
  @Override
  public final <T extends HasUuid> void endVisit(T entity, EntityCodex<T> codex,
      VisitorContext<T> ctx) {
    if (ended.add(entity)) {
      endVisitOnce(entity, codex, ctx);
    }
  }

  public Set<HasUuid> getVisited() {
    return visited;
  }

  /**
   * Delegates to {@link #visitOnce} if {@code entity} has not been visited previously.
   */
  @Override
  public final <T extends HasUuid> boolean visit(T entity, EntityCodex<T> codex,
      VisitorContext<T> ctx) {
    if (visited.add(entity)) {
      return visitOnce(entity, codex, ctx);
    }
    return false;
  }

  protected <T extends HasUuid> void endVisitOnce(T entity, EntityCodex<T> codex,
      VisitorContext<T> ctx) {}

  /**
   * Returns {@link #isGreedy()}.
   */
  protected <T extends HasUuid> boolean visitOnce(T entity, EntityCodex<T> codex,
      VisitorContext<T> ctx) {
    return isGreedy();
  }

}
