package com.getperka.flatpack.policy.visitors;
/*
 * #%L
 * FlatPack Security Policy
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

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;
import static com.getperka.flatpack.util.FlatPackCollections.mapForIteration;

import java.util.List;
import java.util.Map;

import com.getperka.flatpack.policy.pst.HasName;
import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PolicyNode;

/**
 * A simple hierarchical scope for resolving identifiers.
 */
class NodeScope {
  private final Map<Ident<?>, HasName<?>> namedThings = mapForIteration();
  private final NodeScope parent;

  public NodeScope() {
    parent = null;
  }

  private NodeScope(NodeScope parent) {
    this.parent = parent;
  }

  /**
   * Finds all known nodes of a particular type.
   */
  public <T extends HasName<?>> List<T> get(Class<T> clazz) {
    List<T> toReturn = listForAny();
    for (HasName<?> named : namedThings.values()) {
      if (clazz.isInstance(named)) {
        toReturn.add(clazz.cast(named));
      }
    }
    if (parent != null) {
      toReturn.addAll(parent.get(clazz));
    }
    return toReturn;
  }

  /**
   * Find a named {@link PolicyNode} of the requested type with given name.
   */
  public <T extends HasName<R>, R> T get(Class<T> nodeType, Class<R> referentType,
      String simpleName) {
    return get(nodeType, new Ident<R>(referentType, simpleName));
  }

  /**
   * Find a named {@link PolicyNode} with the given name.
   */
  public <T extends HasName<R>, R> T get(Class<T> nodeType, Ident<R> name) {
    T toReturn = nodeType.cast(namedThings.get(name));
    return toReturn != null ? toReturn : parent != null ? parent.get(nodeType, name) : null;
  }

  /**
   * Find a named {@link PolicyNode} with the given name.
   */
  public <T extends HasName<T>> T get(Ident<T> name) {
    return get(name.getReferentType(), name);
  }

  public NodeScope newScope() {
    return new NodeScope(this);
  }

  public void put(HasName<?> named) {
    namedThings.put(named.getName(), named);
  }

  public <T> void put(Ident<T> name, HasName<T> named) {
    namedThings.put(name, named);
  }
}