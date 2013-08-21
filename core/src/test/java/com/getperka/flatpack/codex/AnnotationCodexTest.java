package com.getperka.flatpack.codex;

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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Inject;

import org.junit.Test;

import com.getperka.flatpack.FlatPackEntity;
import com.getperka.flatpack.FlatPackTest;
import com.getperka.flatpack.Packer;
import com.getperka.flatpack.Unpacker;
import com.getperka.flatpack.codex.AnnotationCodexTest.MyAnnotation;
import com.getperka.flatpack.codex.AnnotationCodexTest.OtherAnnotation;
import com.getperka.flatpack.codexes.AnnotationCodex;
import com.getperka.flatpack.codexes.AnnotationInfo;
import com.getperka.flatpack.codexes.UnknownAnnotation;
import com.google.gson.JsonElement;
import com.google.inject.TypeLiteral;

@MyAnnotation(i = 5)
@OtherAnnotation(@MyAnnotation(i = 10))
public class AnnotationCodexTest extends FlatPackTest {
  @Retention(RetentionPolicy.RUNTIME)
  @interface MyAnnotation {
    int i();

    int j() default 1;

    int[] k() default { 2, 3 };
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface OtherAnnotation {
    MyAnnotation[] value();
  }

  @Inject
  private TypeLiteral<AnnotationCodex> codex;
  @Inject
  private Packer packer;
  @Inject
  private Unpacker unpacker;

  @Test
  public void test() {
    MyAnnotation a = getClass().getAnnotation(MyAnnotation.class);
    MyAnnotation a2 = (MyAnnotation) testCodex(codex, a);
    assertEquals(a2, a2);
    assertEquals(a2, a);
    assertEquals(a, a2);
    assertEquals(5, a2.i());
    assertEquals(1, a2.j());
    assertEquals(2, a2.k()[0]);
    assertEquals(3, a2.k()[1]);

    OtherAnnotation o = getClass().getAnnotation(OtherAnnotation.class);
    OtherAnnotation o2 = (OtherAnnotation) testCodex(codex, o);
    assertEquals(o2, o2);
    assertEquals(o, o2);
    assertEquals(o2, o);
    assertEquals(10, o2.value()[0].i());
    assertEquals(2, o2.value()[0].k()[0]);
    assertEquals(3, o2.value()[0].k()[1]);
  }

  @Test
  public void testUnknownAnnotation() throws IOException {
    FlatPackEntity<MyAnnotation> entity =
        new FlatPackEntity<MyAnnotation>() {}
            .withValue(getClass().getAnnotation(MyAnnotation.class));

    JsonElement elt = packer.pack(entity);
    elt.getAsJsonObject().get("value").getAsJsonObject().addProperty("@", "UnknownType");

    Annotation a = unpacker.<Annotation> unpack(Annotation.class, elt, null).getValue();
    assertTrue(a instanceof UnknownAnnotation);
    AnnotationInfo info = (AnnotationInfo) a;
    assertEquals("UnknownType", info.getAnnotationTypeName());
    assertEquals(5, ((Number) info.getAnnotationValues().get("i")).intValue());
  }
}
