package com.getperka.flatpack.ext;
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
import java.util.List;

import org.junit.Test;

import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.codexes.ValueCodex;
import com.getperka.flatpack.ext.VisitorContext.ArrayContext;
import com.getperka.flatpack.ext.VisitorContext.ImmutableContext;
import com.getperka.flatpack.ext.VisitorContext.IterableContext;
import com.getperka.flatpack.ext.VisitorContext.ListContext;
import com.getperka.flatpack.ext.VisitorContext.SingletonContext;
import com.google.gson.JsonElement;

/**
 * Verify behaviors of standard {@link VisitorContext} implementations.
 */
public class VisitorContextTest {
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

  @Test
  public void testArrayContext() {
    ArrayContext<Object> ctx = new ArrayContext<Object>();
    assertTrue(ctx.canReplace());

    final Object replacement = "c";
    ValueRecorder recorder = new ValueRecorder() {
      @Override
      @SuppressWarnings("unchecked")
      public <T> boolean visitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
        ctx.replace((T) replacement);
        return super.visitValue(value, codex, ctx);
      }

    };
    Object[] array = new Object[] { "a", "b" };
    ctx.acceptArray(recorder, array, new PassthroughCodex());
    assertTrue(ctx.didReplace());
    assertEquals(Arrays.asList("a", "a", "b", "b"), recorder.values);
    assertEquals(Arrays.asList("c", "c"), Arrays.asList(array));
  }

  /**
   * Test the default behaviors of the VisitorContext base type so other tests can concentrate on
   * subclass-specific behaviors.
   */
  @Test
  public void testImmutableContext() {
    ImmutableContext<Object> ctx = new ImmutableContext<Object>();
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
    ctx.acceptImmutable(recorder, value, new PassthroughCodex());
    assertEquals(Arrays.asList(value, value), recorder.values);
  }

  @Test
  public void testIterableContext() {
    IterableContext<Object> ctx = new IterableContext<Object>();
    assertTrue(ctx.canRemove());

    ValueRecorder recorder = new ValueRecorder() {
      @Override
      public <T> boolean visitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
        ctx.remove();
        return super.visitValue(value, codex, ctx);
      }

    };
    List<Object> iterable = new ArrayList<Object>(Arrays.asList("a", "b"));
    ctx.acceptIterable(recorder, iterable, new PassthroughCodex());
    assertEquals(Arrays.asList("a", "a", "b", "b"), recorder.values);
    assertTrue(iterable.isEmpty());
  }

  @Test
  public void testListContext() {
    ListContext<Object> ctx = new ListContext<Object>();
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
            ctx.insertBefore((T) String.valueOf(count));
            break;
          case 1:
            assertEquals("b", value);
            ctx.insertAfter((T) String.valueOf(count));
            break;
          case 2:
            assertEquals("c", value);
            ctx.remove();
            break;
          case 3:
            assertEquals("d", value);
            ctx.replace((T) String.valueOf(count));
            break;
          default:
            throw new RuntimeException(String.valueOf(count));
        }
        count++;
        return super.visitValue(value, codex, ctx);
      }

    };
    List<Object> list = new ArrayList<Object>(Arrays.asList("a", "b", "c", "d"));
    ctx.acceptList(recorder, list, new PassthroughCodex());
    assertEquals(Arrays.asList("0", "a", "b", "1", "3"), list);
    assertEquals(Arrays.asList("a", "a", "b", "b", "c", "c", "d", "d"), recorder.values);
    assertTrue(ctx.didInsert());
    assertTrue(ctx.didRemove());
    assertTrue(ctx.didReplace());
  }

  @Test
  public void testSingletonContext() {
    SingletonContext<Object> ctx = new SingletonContext<Object>();
    assertTrue(ctx.canReplace());
    assertNull(ctx.getValue());
    Object value = new Object();
    ctx.replace(value);
    assertTrue(ctx.didReplace());
    assertSame(value, ctx.getValue());

    ValueRecorder recorder = new ValueRecorder();
    ctx.acceptSingleton(recorder, value, new PassthroughCodex());
    assertEquals(Arrays.asList(value, value), recorder.values);
  }
}
