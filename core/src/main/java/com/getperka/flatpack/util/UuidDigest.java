package com.getperka.flatpack.util;

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

import static com.getperka.flatpack.util.FlatPackTypes.UTF8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

import com.getperka.flatpack.HasUuid;

public class UuidDigest {
  private static final byte[] nullString = new byte[0];
  private static final byte[] nullUuid = new byte[0];

  private int counter;
  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

  public UuidDigest(Class<?> owner) {
    add(owner.getName());
  }

  public UuidDigest add(HasUuid entity) {
    if (entity != null) {
      add(entity.getUuid());
    } else {
      silentWrite(nullUuid);
    }
    return this;
  }

  public UuidDigest add(String string) {
    if (string == null) {
      silentWrite(nullString);
    } else {
      silentWrite(string.getBytes(UTF8));
    }
    return this;
  }

  public UuidDigest add(UUID uuid) {
    if (uuid == null) {
      silentWrite(nullUuid);
      return this;
    }

    long high = Long.reverseBytes(uuid.getMostSignificantBits());
    long low = Long.reverseBytes(uuid.getLeastSignificantBits());
    for (int i = 0; i < 8; i++) {
      bytes.write((byte) (high & 0xff));
      high >>>= 8;
    }
    for (int i = 0; i < 8; i++) {
      bytes.write((byte) (low & 0xff));
      low >>>= 8;
    }
    return this;
  }

  public UuidDigest addEntities(Collection<? extends HasUuid> entities) {
    if (entities == null) {
      return this;
    }
    for (HasUuid entity : entities) {
      add(entity);
    }
    return this;
  }

  public UuidDigest addStrings(Collection<String> strings) {
    if (strings == null) {
      return this;
    }
    for (String string : strings) {
      add(string);
    }
    return this;
  }

  public UUID digest() {
    return UUID.nameUUIDFromBytes(bytes.toByteArray());
  }

  private void silentWrite(byte[] data) {
    try {
      bytes.write(counter++);
      bytes.write(data);
    } catch (IOException ignored) {}
  }
}
