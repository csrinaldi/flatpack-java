package com.getperka.flatpack.util;

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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.getperka.flatpack.Packer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A utility class to allow packed FlatPackEntities to be merged together without reifying them.
 * 
 * @see Packer#pack(com.getperka.flatpack.FlatPackEntity)
 */
public class FlatPackEntityMerge {

  public static JsonElement merge(Collection<? extends JsonElement> packs) {
    return new FlatPackEntityMerge(packs).merge();
  }

  /**
   * Merge several
   * 
   * @param packs
   * @return
   */
  public static JsonElement merge(JsonElement... packs) {
    return merge(Arrays.asList(packs));
  }

  private final Map<String, Map<UUID, JsonObject>> data = FlatPackCollections.mapForIteration();
  private final Iterator<? extends JsonElement> iterator;

  private FlatPackEntityMerge(Collection<? extends JsonElement> packs) {
    iterator = packs.iterator();
  }

  /**
   * Reassemble the type x uuid -> data map in {@link #data} into a single {@link JsonObject}.
   */
  private JsonObject collateData() {
    JsonObject toReturn = new JsonObject();
    for (Map.Entry<String, Map<UUID, JsonObject>> entry : data.entrySet()) {
      JsonArray array = new JsonArray();

      for (JsonObject entity : entry.getValue().values()) {
        array.add(entity);
      }

      toReturn.add(entry.getKey(), array);
    }
    return toReturn;
  }

  private JsonObject merge() {
    JsonObject toReturn = new JsonObject();
    JsonObject last = null;
    while (iterator.hasNext()) {
      last = iterator.next().getAsJsonObject();
      mergeOnePack(last, toReturn);
    }
    // Copy only properties from the last entry
    for (Map.Entry<String, JsonElement> entry : last.entrySet()) {
      toReturn.add(entry.getKey(), entry.getValue());
    }
    toReturn.add("data", collateData());
    return toReturn;
  }

  /**
   * Merges a map of type names to arrays of data values.
   */
  private void mergeDataSection(JsonObject source) {
    for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
      String entityName = entry.getKey();
      JsonArray entityArray = entry.getValue().getAsJsonArray();

      Map<UUID, JsonObject> map = data.get(entityName);
      if (map == null) {
        map = FlatPackCollections.mapForIteration();
        data.put(entityName, map);
      }
      for (JsonElement entityData : entityArray) {
        mergeEntityValues(entityData.getAsJsonObject(), map);
      }
    }
  }

  /**
   * Merge the JsonObject for a single entity's data into a correlation map.
   */
  private void mergeEntityValues(JsonObject source, Map<UUID, JsonObject> map) {
    UUID uuid = UUID.fromString(source.get("uuid").getAsString());
    JsonObject dest = map.get(uuid);
    if (dest == null) {
      dest = new JsonObject();
      map.put(uuid, dest);
    }
    for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
      dest.add(entry.getKey(), entry.getValue());
    }
  }

  private void mergeOnePack(JsonObject source, JsonObject toReturn) {
    if (source.has("data")) {
      mergeDataSection(source.get("data").getAsJsonObject());
    }
  }
}
