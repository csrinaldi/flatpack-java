package com.getperka.flatpack.client.dto;

/*
 * #%L
 * FlatPack Client
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

import java.util.UUID;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.util.UuidDigest;

/**
 * Associates a {@link Type} with a documentation string.
 */
public class TypeDescription extends BaseHasUuid {
  private String docString;
  private Type type;

  public String getDocString() {
    return docString;
  }

  public Type getType() {
    return type;
  }

  public void setDocString(String docString) {
    this.docString = docString;
  }

  public void setType(Type type) {
    this.type = type;
  }

  @Override
  protected UUID defaultUuid() {
    return new UuidDigest(getClass()).add(type).add(docString).digest();
  }
}
