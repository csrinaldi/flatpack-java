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
package com.getperka.flatpack.visitors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Provider;

import org.junit.Test;

import com.getperka.flatpack.FlatPackTest;
import com.getperka.flatpack.Visitors;
import com.getperka.flatpack.codexes.EntityCodex;
import com.getperka.flatpack.domain.Employee;
import com.getperka.flatpack.ext.DeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class PackReaderTest extends FlatPackTest {

  @Inject
  private EntityCodex<Employee> employeeCodex;
  @Inject
  private Provider<PackReader> readers;
  @Inject
  private Visitors visitors;
  @Inject
  private Provider<PackWriter> writers;

  @Test
  public void testReadWriteProperties() {
    assertEquals("employee", employeeCodex.describe().getName());

    Employee e1 = makeEmployee();

    StringWriter out = new StringWriter();
    serializationContext(out);
    try {
      visitors.visit(writers.get(), e1);
    } finally {
      closeContext();
    }

    JsonObject obj = new JsonParser().parse(out.toString()).getAsJsonObject();
    // Check embedded properties
    assertFalse(obj.has("address"));
    assertTrue(obj.has("street"));

    Employee e2;
    DeserializationContext d = deserializationContext();
    try {
      e2 = employeeCodex.allocate(obj, d);

      PackReader reader = readers.get();
      reader.setPayload(obj);
      visitors.visit(reader, e2);

      // Check referential integrity
      assertSame(e2, employeeCodex.read(new JsonPrimitive(e2.getUuid().toString()), d));
    } finally {
      d.runPostWork();
      closeContext();
    }

    // Verify callbacks called
    assertTrue(e2.employeePostUnpack);
    assertTrue(e2.employeePreUnpack);
    assertTrue(e2.employeePre1Unpack);

    check(e1, e2);
  }

  /**
   * Verify a property with only a setter works as a write-only property.
   */
  @Test
  public void testWriteOnlyProperty() {
    UUID uuid = UUID.randomUUID();

    JsonObject obj = new JsonObject();
    obj.addProperty("uuid", uuid.toString());
    obj.addProperty("writeOnlyProperty", "Hello World!");

    Employee e;
    DeserializationContext d = deserializationContext();
    try {
      e = employeeCodex.allocate(obj, d);
      PackReader reader = readers.get();
      reader.setPayload(obj);
      visitors.visit(reader, e);
    } finally {
      closeContext();
    }
    assertEquals("Hello World!", e.writeOnlyProperty);

    StringWriter out = new StringWriter();
    serializationContext(out);
    try {
      visitors.visit(writers.get(), e);
    } finally {
      closeContext();
    }
    obj = new JsonParser().parse(out.toString()).getAsJsonObject();
    assertEquals(2, obj.entrySet().size());
    assertTrue(obj.has("uuid"));
    assertTrue(obj.has("employeeNumber"));
    assertFalse(obj.has("writeOnlyProperty"));
  }
}
