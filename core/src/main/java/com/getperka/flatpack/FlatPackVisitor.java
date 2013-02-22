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

import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.VisitorContext;

public class FlatPackVisitor {
  public <T> void endVisit(FlatPackEntity<T> x, VisitorContext<FlatPackEntity<T>> ctx) {}

  public void endVisit(Property property, VisitorContext<Property> ctx) {}

  public <T extends HasUuid> void endVisit(T entity, VisitorContext<T> ctx) {}

  public <T> void endVisitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {}

  public <T> boolean visit(FlatPackEntity<T> x, VisitorContext<FlatPackEntity<T>> ctx) {
    return true;
  }

  public boolean visit(Property property, VisitorContext<Property> ctx) {
    return true;
  }

  public <T extends HasUuid> boolean visit(T entity, VisitorContext<T> ctx) {
    return true;
  }

  public <T> boolean visitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
    return true;
  }
}
