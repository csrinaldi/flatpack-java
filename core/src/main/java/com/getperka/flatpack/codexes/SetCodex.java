/*
 * #%L
 * FlatPack serialization code
 * %%
 * Copyright (C) 2012 Perka Inc.
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
package com.getperka.flatpack.codexes;

import java.util.Set;

import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.util.FlatPackCollections;

/**
 * Support for Sets.
 * 
 * @param <V> the element type of the set
 */
public class SetCodex<V> extends CollectionCodex<Set<V>, V> {

  protected SetCodex() {}

  @Override
  public void acceptNotNull(FlatPackVisitor visitor, Set<V> value, VisitorContext<Set<V>> context) {
    if (visitor.visitValue(value, this, context)) {
      context.walkIterable(getValueCodex()).accept(visitor, value);
    }
    visitor.endVisitValue(value, this, context);
  }

  @Override
  protected Set<V> newCollection() {
    return FlatPackCollections.setForIteration();
  }
}
