package com.getperka.flatpack.policy.pst;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

public class IdentTest {

  @Test
  public void testEquals() {
    Ident<Object> a = simple("a");
    assertTrue(a.equals(a));
    Ident<Object> a2 = simple("a");
    assertTrue(a.equals(a2));
    assertEquals(a.hashCode(), a2.hashCode());

    assertFalse(a.equals(new Ident<String>(String.class, "a")));

    Ident<Object> c = compound("b", "c");
    Ident<Object> c2 = compound("b", "c");
    assertTrue(c.equals(c2));
    assertEquals(c.hashCode(), c2.hashCode());

    Ident<String> cs = new Ident<String>(String.class, simple("b"), simple("c"));
    assertFalse(c.equals(cs));
    assertFalse(a.equals(c));
    assertFalse(c.equals(a));
  }

  @Test
  public void testRemoveIdents() {
    Ident<Object> i = compound("a", "b", "c");
    assertEquals("a.b.c", i.toString());
    i = i.removeLeadingIdent();
    assertEquals("b.c", i.toString());
    i = i.removeLeadingIdent();
    assertEquals("c", i.toString());
    try {
      i.removeLeadingIdent();
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  private Ident<Object> compound(String... names) {
    List<Ident<?>> parts = listForAny();
    for (String name : names) {
      parts.add(simple(name));
    }
    return new Ident<Object>(Object.class, parts);
  }

  private Ident<Object> simple(String name) {
    return new Ident<Object>(Object.class, name);
  }
}
