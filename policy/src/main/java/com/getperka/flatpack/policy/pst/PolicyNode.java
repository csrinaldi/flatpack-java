package com.getperka.flatpack.policy.pst;
/*
 * #%L
 * FlatPack Security Policy
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

import java.util.ArrayList;
import java.util.List;

import com.getperka.flatpack.policy.visitors.ToSourceVisitor;
import com.getperka.flatpack.policy.visitors.ToStringVisitor;

public abstract class PolicyNode {
  /**
   * Convenience method for constructing a generic {@link ArrayList}.
   */
  protected static <T> List<T> list() {
    return new ArrayList<T>();
  }

  private int lineNumber = -1;

  public abstract void accept(PolicyVisitor v);

  public int getLineNumber() {
    return lineNumber;
  }

  public void setLineNumber(int lineNumber) {
    this.lineNumber = lineNumber;
  }

  /**
   * Returns a canonical representation of the PolicyNode.
   */
  public String toSource() {
    return toString(new ToSourceVisitor());
  }

  /**
   * Returns a summary of the PolicyNode. For debugging use only.
   */
  @Override
  public String toString() {
    return toString(new ToStringVisitor());
  }

  private String toString(PolicyVisitor v) {
    accept(v);
    return v.toString();
  }
}