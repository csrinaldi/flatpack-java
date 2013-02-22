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

import java.util.Collection;
import java.util.List;

import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.ext.VisitorContext.IterableContext;
import com.getperka.flatpack.ext.VisitorContext.ListContext;
import com.getperka.flatpack.util.FlatPackCollections;

/**
 * List support. This class is parameterized with {@link Collection} to gracefully handle receiving
 * set or other collection types.
 * 
 * @param <V> the element type of the list
 */
public class ListCodex<V> extends CollectionCodex<Collection<V>, V> {
  protected ListCodex() {}

  @Override
  public void acceptNotNull(FlatPackVisitor visitor, Collection<V> value,
      VisitorContext<Collection<V>> context) {
    if (visitor.visitValue(value, this, context)) {
      if (value instanceof List) {
        new ListContext<V>().acceptList(visitor, (List<V>) value, getValueCodex());
      } else {
        new IterableContext<V>().acceptIterable(visitor, value, getValueCodex());
      }
    }
    visitor.endVisitValue(value, this, context);
  }

  @Override
  protected List<V> newCollection() {
    return FlatPackCollections.listForAny();
  }
}
