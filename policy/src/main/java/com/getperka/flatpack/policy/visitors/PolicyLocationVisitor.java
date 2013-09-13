package com.getperka.flatpack.policy.visitors;
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

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.getperka.flatpack.policy.pst.PolicyNode;
import com.getperka.flatpack.policy.pst.PolicyVisitor;

/**
 * A utility class for policy visitors that maintains the current location of the visitor to improve
 * error messages.
 */
public class PolicyLocationVisitor extends PolicyVisitor {

  private final Deque<PolicyNode> location = new ArrayDeque<PolicyNode>();

  protected List<PolicyNode> currentLocation() {
    List<PolicyNode> toReturn = listForAny();
    toReturn.addAll(location);
    return toReturn;
  }

  protected <P extends PolicyNode> P currentLocation(Class<P> clazz) {
    for (PolicyNode x : location) {
      if (clazz.isInstance(x)) {
        return clazz.cast(x);
      }
    }
    return null;
  }

  protected void doTraverse(PolicyNode x) {
    super.traverse(x);
  }

  protected String summarizeLocation() {
    if (location.isEmpty()) {
      return null;
    }
    PolicyNode node = location.peek();

    String toReturn = node.toString();

    if (node.getLineNumber() == -1) {
      toReturn += " (Unknown line)";
    } else {
      toReturn += " (Line " + node.getLineNumber() + ")";
    }
    return toReturn;
  }

  @Override
  protected final void traverse(PolicyNode x) {
    if (x != null) {
      location.push(x);
    }
    try {
      doTraverse(x);
    } finally {
      if (x != null) {
        location.pop();
      }
    }
  }
}
