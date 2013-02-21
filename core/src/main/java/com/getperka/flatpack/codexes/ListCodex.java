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

import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;

import com.getperka.flatpack.PackVisitor;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.ext.VisitorContext.ListContext;
import com.getperka.flatpack.util.FlatPackCollections;

/**
 * List support.
 * 
 * @param <V> the element type of the list
 */
public class ListCodex<V> extends CollectionCodex<List<V>, V> {
  @Inject
  Provider<ListContext<V>> contexts;

  protected ListCodex() {}

  @Override
  public void acceptNotNull(PackVisitor visitor, List<V> value, VisitorContext<List<V>> context) {
    if (visitor.visitValue(value, this, context)) {
      contexts.get().acceptList(visitor, value, getValueCodex());
    }
    visitor.endVisitValue(value, this, context);
  }

  @Override
  protected List<V> newCollection() {
    return FlatPackCollections.listForAny();
  }
}
