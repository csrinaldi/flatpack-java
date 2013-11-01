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
 * Creates clones of {@link PolicyNode} instances.
 */
public class PolicyCloner extends PolicyVisitor {

  /**
   * Each visit method should push its cloned node onto the stack.
   */
  private static class Cloner extends PolicyVisitor {
    private final Deque<Object> stack = new ArrayDeque<Object>();

    @Override
    public boolean visit(ActionDefinition x) {
      ActionDefinition n = new ActionDefinition();
      n.setActions(clone(x.getActions()));
      n.setName(clone(x.getName()));
      stack.push(n);
      return false;
    }

    @Override
    public boolean visit(AllowBlock x) {
      AllowBlock n = new AllowBlock();
      n.setAclRules(clone(x.getAclRules()));
      n.setInheritFrom(clone(x.getInheritFrom()));
      n.setOnly(x.isOnly());
      stack.push(n);
      return false;
    }

    @Override
    public boolean visit(AllowRule x) {
      AllowRule n = new AllowRule();
      n.setGroupName(clone(x.getGroupName()));
      n.setSecurityActions(clone(x.getSecurityActions()));
      stack.push(n);
      return false;
    }

    @Override
    public boolean visit(GroupBlock x) {
      GroupBlock n = new GroupBlock();
      n.setDefinitions(clone(x.getDefinitions()));
      n.setInheritFrom(clone(x.getInheritFrom()));
      stack.push(n);
      return false;
    }

    @Override
    public boolean visit(GroupDefinition x) {
      GroupDefinition n = new GroupDefinition();
      n.setName(clone(x.getName()));
      n.setPaths(clone(x.getPaths()));
      stack.push(n);
      return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public boolean visit(Ident<?> x) {
      Ident n;
      if (x.isCompound()) {
        n = new Ident(x.getReferentType(), x.getCompoundName());
      } else if (x.isSimple()) {
        n = new Ident(x.getReferentType(), x.getSimpleName());
      } else {
        throw new UnsupportedOperationException();
      }
      n.setReferent(x.getReferent());
      stack.push(n);
      return false;
    }

    @Override
    public boolean visit(PackagePolicy x) {
      PackagePolicy n = new PackagePolicy();
      n.setAllows(clone(x.getAllows()));
      n.setName(clone(x.getName()));
      n.setPackagePolicies(clone(x.getPackagePolicies()));
      n.setTypePolicies(clone(x.getTypePolicies()));
      n.setVerbs(clone(x.getVerbs()));
      stack.push(n);
      return false;
    }

    @Override
    public boolean visit(PolicyFile x) {
      PolicyFile n = new PolicyFile();
      n.setAllows(clone(x.getAllows()));
      n.setPackagePolicies(clone(x.getPackagePolicies()));
      n.setTypePolicies(clone(x.getTypePolicies()));
      n.setVerbs(clone(x.getVerbs()));
      stack.push(n);
      return false;
    }

    @Override
    public boolean visit(PropertyList x) {
      PropertyList n = new PropertyList();
      n.setPropertyNames(clone(x.getPropertyNames()));
      stack.push(n);
      return false;
    }

    @Override
    public boolean visit(PropertyPolicy x) {
      PropertyPolicy n = new PropertyPolicy();
      n.setAllows(clone(x.getAllows()));
      n.setName(clone(x.getName()));
      n.setPropertyLists(clone(x.getPropertyLists()));
      stack.push(n);
      return false;
    }

    @Override
    public boolean visit(TypePolicy x) {
      TypePolicy n = new TypePolicy();
      n.setAllows(clone(x.getAllows()));
      n.setGroups(clone(x.getGroups()));
      n.setName(clone(x.getName()));
      n.setPolicies(clone(x.getPolicies()));
      n.setVerbs(clone(x.getVerbs()));
      stack.push(n);
      return false;
    }

    /**
     * Fail definitively if a new node type is introduced.
     */
    @Override
    protected boolean defaultVisit() {
      throw new UnsupportedOperationException("Missing visit() method for unexpected node type");
    }

    private <T extends PolicyNode> List<T> clone(List<T> toClone) {
      if (toClone == null) {
        return null;
      }
      List<T> toReturn = listForAny();
      for (T node : toClone) {
        toReturn.add(clone(node));
      }
      return toReturn;
    }

    @SuppressWarnings("unchecked")
    private <T extends PolicyNode> T clone(T toClone) {
      if (toClone == null) {
        return null;
      }
      traverse(toClone);
      T toReturn = (T) stack.pop();
      toReturn.setLineNumber(toClone.getLineNumber());
      return toReturn;
    }
  }

  public <T extends PolicyNode> List<T> clone(List<T> nodes) {
    return new Cloner().clone(nodes);
  }

  public <T extends PolicyNode> T clone(T node) {
    return new Cloner().clone(node);
  }
}
