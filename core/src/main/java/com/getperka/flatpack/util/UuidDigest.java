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
  private static final byte[] nullUuid = new byte[16];

  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

  public UuidDigest add(Collection<? extends HasUuid> entities) {
    for (HasUuid entity : entities) {
      add(entity);
    }
    return this;
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
    silentWrite(string.getBytes(UTF8));
    return this;
  }

  public UuidDigest add(UUID uuid) {
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

  public UUID digest() {
    return UUID.nameUUIDFromBytes(bytes.toByteArray());
  }

  private void silentWrite(byte[] data) {
    try {
      bytes.write(data);
    } catch (IOException ignored) {}
  }
}
