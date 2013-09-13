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

import java.util.List;


public class PolicyVisitor {
  public void endVisit(AclRule x) {}

  public void endVisit(Allow x) {}

  public void endVisit(Group x) {}

  public void endVisit(GroupDefinition x) {}

  public void endVisit(Ident<?> x) {}

  public void endVisit(PolicyFile x) {}

  public void endVisit(PropertyList x) {}

  public void endVisit(PropertyPolicy x) {}

  public void endVisit(TypePolicy x) {}

  public void endVisit(Verb x) {}

  public boolean visit(AclRule x) {
    return defaultVisit();
  }

  public boolean visit(Allow x) {
    return defaultVisit();
  }

  public boolean visit(Group x) {
    return defaultVisit();
  }

  public boolean visit(GroupDefinition x) {
    return defaultVisit();
  }

  public boolean visit(Ident<?> x) {
    return defaultVisit();
  }

  public boolean visit(PolicyFile x) {
    return defaultVisit();
  }

  public boolean visit(PropertyList x) {
    return defaultVisit();
  }

  public boolean visit(PropertyPolicy x) {
    return defaultVisit();
  }

  public boolean visit(TypePolicy x) {
    return defaultVisit();
  }

  public boolean visit(Verb x) {
    return defaultVisit();
  }

  protected boolean defaultVisit() {
    return true;
  }

  /**
   * Traverse a list of nodes. This method is null-safe.
   */
  protected void traverse(List<? extends PolicyNode> list) {
    if (list == null) {
      return;
    }
    for (PolicyNode x : list) {
      traverse(x);
    }
  }

  /**
   * Traverse a single node. This method should be called in preference to calling
   * {@link PolicyNode#accept(PolicyVisitor)} directly, since subclasses of PolicyVisitor may wish
   * to influence the traversal logic. It is also null-safe.
   */
  protected void traverse(PolicyNode x) {
    if (x != null) {
      x.accept(this);
    }
  }
}