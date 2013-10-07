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

import com.getperka.flatpack.policy.pst.ActionDefinition;
import com.getperka.flatpack.policy.pst.AllowBlock;
import com.getperka.flatpack.policy.pst.AllowRule;
import com.getperka.flatpack.policy.pst.GroupBlock;
import com.getperka.flatpack.policy.pst.GroupDefinition;
import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PackagePolicy;
import com.getperka.flatpack.policy.pst.PolicyFile;
import com.getperka.flatpack.policy.pst.PolicyNode;
import com.getperka.flatpack.policy.pst.PropertyList;
import com.getperka.flatpack.policy.pst.PropertyPolicy;
import com.getperka.flatpack.policy.pst.TypePolicy;

/**
 * A base type for {@link PolicyNode} visitors.
 */
public class PolicyVisitor {
  public void endVisit(ActionDefinition x) {}

  public void endVisit(AllowBlock x) {}

  public void endVisit(AllowRule x) {}

  public void endVisit(GroupBlock x) {}

  public void endVisit(GroupDefinition x) {}

  public void endVisit(Ident<?> x) {}

  public void endVisit(PackagePolicy x) {}

  public void endVisit(PolicyFile x) {}

  public void endVisit(PropertyList x) {}

  public void endVisit(PropertyPolicy x) {}

  public void endVisit(TypePolicy x) {}

  /**
   * Traverse a list of nodes. This method is null-safe.
   */
  public void traverse(List<? extends PolicyNode> list) {
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
  public void traverse(PolicyNode x) {
    if (x != null) {
      x.accept(this);
    }
  }

  public boolean visit(ActionDefinition x) {
    return defaultVisit();
  }

  public boolean visit(AllowBlock x) {
    return defaultVisit();
  }

  public boolean visit(AllowRule x) {
    return defaultVisit();
  }

  public boolean visit(GroupBlock x) {
    return defaultVisit();
  }

  public boolean visit(GroupDefinition x) {
    return defaultVisit();
  }

  public boolean visit(Ident<?> x) {
    return defaultVisit();
  }

  public boolean visit(PackagePolicy x) {
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

  protected boolean defaultVisit() {
    return true;
  }
}