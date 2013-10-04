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

import java.io.IOException;
import java.io.Reader;

/**
 * Utility methods for reading and writing things.
 */
public class FlatPackStreams {
  public static String read(Reader contents) throws IOException {
    StringBuilder sb = new StringBuilder();
    char[] chars = new char[4096];
    for (int read = contents.read(chars); read != -1; read = contents.read(chars)) {
      sb.append(chars, 0, read);
    }
    return sb.toString();
  }
}
