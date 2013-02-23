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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.junit.Test;

import com.getperka.flatpack.FlatPackTest;
import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.Visitors;
import com.getperka.flatpack.codexes.ValueCodex;
import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.DeserializationContext;
import com.getperka.flatpack.ext.SerializationContext;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.ext.VisitorContext;
import com.google.gson.JsonElement;

/**
 * Verify behaviors of standard {@link VisitorContext} implementations.
 */
public class VisitorContextTest extends FlatPackTest {
  /**
   * Just used as a trivial codex to pump {@link FlatPackVisitor#visitValue}.
   */
  static class PassthroughCodex extends ValueCodex<Object> {
    @Override
    public Type describe() {
      return null;
    }

    @Override
    public Object readNotNull(JsonElement element, DeserializationContext context) throws Exception {
      return null;
    }

    @Override
    public void writeNotNull(Object object, SerializationContext context) throws Exception {}
  }

  static class ValueRecorder extends FlatPackVisitor {
    final List<Object> values = new ArrayList<Object>();

    @Override
    public <T> void endVisitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
      values.add(value);
    }

    @Override
    public <T> boolean visitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
      values.add(value);
      return false;
    }
  }

  @Inject
  PassthroughCodex passthrough;

  @Inject
  Visitors visitors;

  @Test
  public void testArrayContext() {
    final Object replacement = "c";
    ValueRecorder recorder = new ValueRecorder() {
      @Override
      public <T> boolean visitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
        assertTrue(ctx.canReplace());
        ctx.replace(codex.cast(replacement));
        return super.visitValue(value, codex, ctx);
      }

    };
    Object[] array = new Object[] { "a", "b" };
    visitors.getWalkers().walkArray(passthrough).accept(recorder, array);
    assertEquals(Arrays.asList("a", "a", "b", "b"), recorder.values);
    assertEquals(Arrays.asList("c", "c"), Arrays.asList(array));
  }

  /**
   * Test the default behaviors of the VisitorContext base type so other tests can concentrate on
   * subclass-specific behaviors.
   */
  @Test
  public void testImmutableContext() {
    VisitorContext<?> ctx = (VisitorContext<?>) visitors.getWalkers().walkImmutable(passthrough);
    assertFalse(ctx.canInsert());
    assertFalse(ctx.canRemove());
    assertFalse(ctx.canReplace());
    assertFalse(ctx.didInsert());
    assertFalse(ctx.didRemove());
    assertFalse(ctx.didReplace());
    try {
      ctx.insertAfter(null);
      fail();
    } catch (UnsupportedOperationException expected) {}
    try {
      ctx.insertBefore(null);
      fail();
    } catch (UnsupportedOperationException expected) {}
    try {
      ctx.remove();
      fail();
    } catch (UnsupportedOperationException expected) {}
    try {
      ctx.replace(null);
      fail();
    } catch (UnsupportedOperationException expected) {}

    ValueRecorder recorder = new ValueRecorder();
    Object value = new Object();
    ctx.walkImmutable(passthrough).accept(recorder, value);
    assertEquals(Arrays.asList(value, value), recorder.values);
  }

  @Test
  public void testIterableContext() {
    VisitorContext<?> ctx = (VisitorContext<?>) visitors.getWalkers().walkIterable(passthrough);
    assertTrue(ctx.canRemove());

    ValueRecorder recorder = new ValueRecorder() {
      @Override
      public <T> boolean visitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
        ctx.remove();
        return super.visitValue(value, codex, ctx);
      }

    };
    List<Object> iterable = new ArrayList<Object>(Arrays.asList("a", "b"));
    ctx.walkIterable(passthrough).accept(recorder, iterable);
    assertEquals(Arrays.asList("a", "a", "b", "b"), recorder.values);
    assertTrue(iterable.isEmpty());
  }

  @Test
  public void testListContext() {
    VisitorContext<?> ctx = (VisitorContext<?>) visitors.getWalkers().walkList(passthrough);
    assertTrue(ctx.canInsert());
    assertTrue(ctx.canReplace());
    assertTrue(ctx.canRemove());

    ValueRecorder recorder = new ValueRecorder() {
      int count = 0;

      @Override
      public <T> boolean visitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
        switch (count) {
          case 0:
            assertEquals("a", value);
            ctx.insertBefore(codex.cast(String.valueOf(count)));
            break;
          case 1:
            assertEquals("b", value);
            ctx.insertAfter(codex.cast(String.valueOf(count)));
            break;
          case 2:
            assertEquals("c", value);
            ctx.remove();
            break;
          case 3:
            assertEquals("d", value);
            ctx.replace(codex.cast(String.valueOf(count)));
            break;
          case 4:
            assertEquals("e", value);
            ctx.insertBefore(codex.cast(String.valueOf(count)));
            break;
          default:
            throw new RuntimeException(String.valueOf(count));
        }
        count++;
        return super.visitValue(value, codex, ctx);
      }

    };
    List<Object> list = new ArrayList<Object>(Arrays.asList("a", "b", "c", "d", "e"));
    ctx.walkList(passthrough).accept(recorder, list);
    assertEquals(Arrays.asList("0", "a", "b", "1", "3", "4", "e"), list);
    assertEquals(Arrays.asList("a", "a", "b", "b", "c", "c", "d", "d", "e", "e"), recorder.values);
  }

  @Test
  public void testListContextEmpty() {
    ValueRecorder recorder = new ValueRecorder() {
      @Override
      public <T> boolean visitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
        fail("Should not see this with an empty list");
        return super.visitValue(value, codex, ctx);
      }
    };

    visitors.getWalkers().walkList(passthrough).accept(recorder, Collections.emptyList());
    assertTrue(recorder.values.isEmpty());
  }

  @Test
  public void testListContextRemoveBefore() {
    ValueRecorder recorder = new ValueRecorder() {
      @Override
      public <T> boolean visitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
        assertEquals("Hello", value);
        ctx.remove();
        ctx.insertBefore(codex.cast("World!"));
        return super.visitValue(value, codex, ctx);
      }
    };
    List<Object> list = new ArrayList<Object>(Arrays.asList("Hello"));
    visitors.getWalkers().walkList(passthrough).accept(recorder, list);
    assertEquals(Collections.singletonList("World!"), list);
    assertEquals(Arrays.asList("Hello", "Hello"), recorder.values);
  }

  @Test
  public void testNullableContext() {
    NullableContext<Object> ctx = (NullableContext<Object>) visitors.getWalkers()
        .walkNullable(passthrough);
    assertTrue(ctx.canRemove());
    assertTrue(ctx.canReplace());
    assertNull(ctx.getValue(null));

    Object value = new Object();
    ctx.replace(value);
    assertTrue(ctx.didReplace());
    assertSame(value, ctx.getValue(new Object()));

    ctx.remove();
    assertNull(ctx.getValue(new Object()));

    // Verify that re-replacing the value will restore
    ctx.replace(value);
    assertSame(value, ctx.getValue(new Object()));

    ValueRecorder recorder = new ValueRecorder();
    ctx.walkSingleton(passthrough).accept(recorder, value);
    assertEquals(Arrays.asList(value, value), recorder.values);
  }

  @Test
  public void testSingletonContext() {
    SingletonContext<Object> ctx = (SingletonContext<Object>) visitors.getWalkers()
        .walkSingleton(passthrough);
    assertTrue(ctx.canReplace());
    assertNull(ctx.getValue(null));
    Object value = new Object();
    ctx.replace(value);
    assertTrue(ctx.didReplace());
    assertSame(value, ctx.getValue(new Object()));

    ValueRecorder recorder = new ValueRecorder();
    ctx.walkSingleton(passthrough).accept(recorder, value);
    assertEquals(Arrays.asList(value, value), recorder.values);
  }
}
