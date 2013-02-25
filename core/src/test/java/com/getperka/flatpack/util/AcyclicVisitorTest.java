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

import static org.junit.Assert.assertTrue;

import java.util.Collections;

import javax.inject.Inject;

import org.junit.Test;

import com.getperka.flatpack.FlatPackTest;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.Visitors;
import com.getperka.flatpack.codexes.EntityCodex;
import com.getperka.flatpack.domain.Employee;
import com.getperka.flatpack.domain.Manager;
import com.getperka.flatpack.ext.VisitorContext;

public class AcyclicVisitorTest extends FlatPackTest {
  static class MyVisitor extends AcyclicVisitor {
    boolean sawEmployee;
    boolean sawManager;

    @Override
    protected <T extends HasUuid> void endVisitOnce(T entity, EntityCodex<T> codex,
        VisitorContext<T> ctx) {
      sawEmployee |= entity instanceof Employee;
      sawManager |= entity instanceof Manager;
    }
  }

  @Inject
  Visitors visitors;

  @Test
  public void test() {
    // Create a cyclic datastructure
    Manager manager = makeManager();
    Employee employee = makeEmployee();

    manager.setEmployees(Collections.singletonList(employee));
    employee.setManager(manager);

    MyVisitor visitor = new MyVisitor();
    visitors.visit(visitor, employee);
    assertTrue(visitor.sawEmployee);
    assertTrue(visitor.sawManager);

    // Ensure the same behavior visiting the other
    visitor = new MyVisitor();
    visitors.visit(visitor, manager);
    assertTrue(visitor.sawEmployee);
    assertTrue(visitor.sawManager);
  }
}
