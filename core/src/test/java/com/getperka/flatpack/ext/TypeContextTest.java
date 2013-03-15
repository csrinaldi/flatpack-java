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

import static com.getperka.flatpack.util.FlatPackTypes.createType;
import static org.junit.Assert.assertSame;

import java.util.List;

import javax.inject.Inject;

import org.junit.Test;

import com.getperka.flatpack.FlatPackTest;

public class TypeContextTest extends FlatPackTest {

  @Inject
  TypeContext typeContext;

  @Test
  public void testCodexCanonicalization() throws NoSuchMethodException {
    Codex<?> c1 = typeContext.getCodex(createType(List.class, String.class));
    Codex<?> c2 = typeContext.getCodex(createType(List.class, String.class));
    Codex<?> c3 = typeContext.getCodex(getClass().getDeclaredMethod("foo").getGenericReturnType());
    assertSame(c1, c2);
    assertSame(c2, c3);
  }

  List<String> foo() {
    return null;
  }
}
