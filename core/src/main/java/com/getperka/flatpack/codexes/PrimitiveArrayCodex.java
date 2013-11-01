package com.getperka.flatpack.codexes;
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

import static com.getperka.flatpack.util.FlatPackTypes.unbox;

import java.lang.reflect.Array;

import javax.inject.Inject;

import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.DeserializationContext;
import com.getperka.flatpack.ext.JsonKind;
import com.getperka.flatpack.ext.SerializationContext;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.ext.TypeContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.google.inject.TypeLiteral;

/**
 * A variation on ArrayCodex that handles the non-trivial differences between primitive arrays and
 * object arrays.
 * 
 * @param <T> a boxed version of a primitive type
 */
public class PrimitiveArrayCodex<T> extends ValueCodex<Object> {
  protected Class<T> boxedType;
  protected Class<?> elementType;
  protected Codex<T> valueCodex;

  /**
   * Requires injection.
   */
  protected PrimitiveArrayCodex() {}

  @Override
  public Type describe() {
    return new Type.Builder()
        .withJsonKind(JsonKind.LIST)
        .withListElement(valueCodex.describe())
        .build();
  }

  @Override
  public Object readNotNull(JsonElement element, DeserializationContext context) throws Exception {
    JsonArray array = element.getAsJsonArray();
    Object toReturn = Array.newInstance(elementType, array.size());
    for (int i = 0, j = array.size(); i < j; i++) {
      Array.set(toReturn, i, valueCodex.read(array.get(i), context));
    }
    return toReturn;
  }

  @Override
  public void writeNotNull(Object object, SerializationContext context) throws Exception {
    JsonWriter writer = context.getWriter();
    writer.beginArray();
    for (int i = 0, j = Array.getLength(object); i < j; i++) {
      context.pushPath("[" + i + "]");
      valueCodex.write(boxedType.cast(Array.get(object, i)), context);
      context.popPath();
    }
    writer.endArray();
  }

  @Inject
  @SuppressWarnings("unchecked")
  void inject(TypeLiteral<T> elementType, TypeContext context) {
    boxedType = (Class<T>) elementType.getRawType();
    this.elementType = unbox(elementType.getRawType());
    valueCodex = (Codex<T>) context.getCodex(elementType.getType());
  }
}
