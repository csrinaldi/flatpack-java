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

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import com.getperka.flatpack.Configuration;
import com.getperka.flatpack.FlatPackEntity;
import com.getperka.flatpack.FlatPackTest;
import com.getperka.flatpack.domain.Employee;
import com.getperka.flatpack.domain.TestTypeSource;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class FlatPackEntityMergeTest extends FlatPackTest {

  @Test
  public void test() throws IOException {
    Employee e1 = makeEmployee();
    Employee e2 = makeEmployee();

    JsonElement j1 = flatpack.getPacker().pack(FlatPackEntity.entity(e1));
    JsonElement j2 = flatpack.getPacker().pack(FlatPackEntity.entity(e2));

    JsonObject merged = FlatPackEntityMerge.merge(j2, j1).getAsJsonObject();

    assertEquals(2, merged.entrySet().size());
    assertEquals(e1.getUuid().toString(), merged.get("value").getAsString());
    JsonArray employeeArray = merged.get("data").getAsJsonObject().get("employee").getAsJsonArray();
    assertEquals(2, employeeArray.size());

    FlatPackEntity<Employee> entity =
        flatpack.getUnpacker().<Employee> unpack(Employee.class, merged, null);
    Employee u1 = entity.getValue();
    assertEquals(e1, u1);
    assertEquals(2, entity.getExtraEntities().size());
  }

  @Override
  protected Configuration getConfiguration() {
    return super.getConfiguration().addTypeSource(new TestTypeSource());
  }

}
