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

import java.util.List;

import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PolicyNode;

/**
 * Elides most subnodes to produce output suitable for use in a debugger.
 */
public class ToStringVisitor extends ToSourceVisitor {

  @Override
  public boolean visit(Ident<?> x) {
    // Always print compound names
    if (x.isCompound()) {
      super.traverse(x.getCompoundName(), ".");
      return false;
    }
    return super.visit(x);
  }

  @Override
  protected void nl() {}

  @Override
  protected void printBlockOrSingleton(List<? extends PolicyNode> list) {
    print(" { " + list.size() + " nodes } ");
  }

  @Override
  protected void traverse(List<? extends PolicyNode> list) {
    print(" ... ");
  }

  @Override
  protected void traverse(List<? extends PolicyNode> list, String separator) {
    print(" ... ");
  }

  @Override
  protected void traverse(PolicyNode x) {
    // Always print single idents, since they make the summary usable
    if (x instanceof Ident) {
      print(x.toSource());
    } else {
      print(" ... ");
    }
  }

}
