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
package com.getperka.flatpack.codexes;

import java.io.IOException;
import java.util.Map;

import javax.inject.Inject;

import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.DelegatingSerializationContext;
import com.getperka.flatpack.ext.DeserializationContext;
import com.getperka.flatpack.ext.JsonKind;
import com.getperka.flatpack.ext.SerializationContext;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.ext.UpdatingCodex;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.util.FlatPackCollections;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.bind.JsonTreeWriter;
import com.google.gson.stream.JsonWriter;
import com.google.inject.TypeLiteral;

/**
 * A map of string-like values to a arbitrary value.
 * 
 * @param <V> the map value type
 */
public class StringMapCodex<K, V> extends UpdatingCodex<Map<K, V>> {
  private static class StealingSerializationContext extends DelegatingSerializationContext {
    private JsonTreeWriter writer;

    public StealingSerializationContext(SerializationContext delegate) {
      super(delegate);
    }

    @Override
    public JsonWriter getWriter() {
      writer = new JsonTreeWriter();
      return writer;
    }

    public JsonElement getWritten() {
      return writer.get();
    }
  }

  /**
   * The key's codex is a ValueCodex because we don't want to try to serialize arbitrarily
   * complicated values into the key.
   */
  private ValueCodex<K> keyCodex;
  private Codex<V> valueCodex;

  protected StringMapCodex() {}

  @Override
  public void acceptNotNull(FlatPackVisitor visitor, Map<K, V> value,
      VisitorContext<Map<K, V>> context) {
    if (visitor.visitValue(value, this, context)) {
      context.walkIterable(valueCodex).accept(visitor, value.values());
    }
    visitor.endVisitValue(value, this, context);
  }

  @Override
  public Type describe() {
    return new Type.Builder()
        .withJsonKind(JsonKind.MAP)
        .withMapKey(keyCodex.describe())
        .withMapValue(valueCodex.describe())
        .build();
  }

  @Override
  public String getPropertySuffix() {
    return valueCodex.getPropertySuffix();
  }

  @Override
  public Map<K, V> readNotNull(JsonElement element, DeserializationContext context)
      throws IOException {
    Map<K, V> toReturn = FlatPackCollections.mapForIteration();
    for (Map.Entry<String, JsonElement> elt : element.getAsJsonObject().entrySet()) {
      context.pushPath("[" + elt.getKey() + "]");
      try {
        K key = keyCodex.read(new JsonPrimitive(elt.getKey()), context);
        V value = valueCodex.read(elt.getValue(), context);
        toReturn.put(key, value);
      } catch (Exception e) {
        context.fail(e);
      } finally {
        context.popPath();
      }
    }
    return toReturn;
  }

  @Override
  public Map<K, V> replacementValueNotNull(Map<K, V> oldValue, Map<K, V> newValue) {
    oldValue.clear();
    oldValue.putAll(newValue);
    return oldValue;
  }

  @Override
  public void writeNotNull(Map<K, V> object, SerializationContext context) throws IOException {
    JsonWriter writer = context.getWriter();
    writer.beginObject();
    for (Map.Entry<K, V> entry : object.entrySet()) {
      context.pushPath("[" + entry.getKey() + "]");
      try {
        StealingSerializationContext stealing = new StealingSerializationContext(context);
        keyCodex.write(entry.getKey(), stealing);
        JsonElement keyJson = stealing.getWritten();

        writer.name(keyJson.getAsString());
        valueCodex.write(entry.getValue(), context);
      } finally {
        context.popPath();
      }
    }
    writer.endObject();
  }

  @Inject
  @SuppressWarnings("unchecked")
  void inject(TypeLiteral<K> keyType, TypeLiteral<V> valueType, TypeContext typeContext) {
    this.keyCodex = (ValueCodex<K>) typeContext.getCodex(keyType.getType());
    this.valueCodex = (Codex<V>) typeContext.getCodex(valueType.getType());
  }
}