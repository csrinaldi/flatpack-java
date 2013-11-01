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

import static com.getperka.flatpack.util.FlatPackCollections.setForIteration;

import java.util.Set;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.policy.StaticPolicy;
import com.getperka.flatpack.policy.pst.AllowRule;
import com.getperka.flatpack.policy.pst.AllowBlock;
import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PackagePolicy;
import com.getperka.flatpack.policy.pst.PolicyFile;
import com.getperka.flatpack.policy.pst.PolicyNode;
import com.getperka.flatpack.policy.pst.PropertyList;
import com.getperka.flatpack.policy.pst.PropertyPolicy;
import com.getperka.flatpack.policy.pst.TypePolicy;
import com.getperka.flatpack.security.GroupPermissions;
import com.getperka.flatpack.security.SecurityAction;
import com.getperka.flatpack.security.SecurityGroup;
import com.getperka.flatpack.security.SecurityTarget;

/**
 * Converts policy tree data into the in-memory datastructures used by {@link StaticPolicy}.
 */
public class PermissionsExtractor extends PolicyLocationVisitor {
  private final GroupPermissions accumulator;
  private final Class<? extends HasUuid> entity;
  private final Property property;

  /**
   * @param accumulator the {@link GroupPermissions} to populate with policy data
   * @param target the target whose policy should be extracted
   */
  public PermissionsExtractor(GroupPermissions accumulator, SecurityTarget target) {
    this.accumulator = accumulator;
    switch (target.getKind()) {
      case GLOBAL:
        entity = null;
        property = null;
        break;
      case ENTITY_PROPERTY:
      case PROPERTY:
        property = target.getProperty();
        entity = property.getEnclosingType().getEntityType();
        break;
      case ENTITY:
      case TYPE:
        property = null;
        entity = target.getEntityType();
        break;
      default:
        throw new UnsupportedOperationException(target.getKind().name());
    }
  }

  /**
   * Populates {@link #accumulator} with the actions in the rule. Visitation is controlled by the
   * other {@code visit} methods choosing to descend into enclosing nodes.
   */
  @Override
  public boolean visit(AllowRule x) {
    Set<SecurityAction> set = setForIteration();
    for (Ident<SecurityAction> ident : x.getSecurityActions()) {
      set.add(ident.getReferent());
    }
    SecurityGroup group = x.getGroupName().getReferent();
    accumulator.addPermissions(group, set);
    return false;
  }

  /**
   * Supports the "only" construct by clearing whatever permissions have already been accumulated.
   */
  @Override
  public boolean visit(AllowBlock x) {
    if (x.isOnly()) {
      accumulator.clear();
    }
    return true;
  }

  /**
   * Descend to find types, the inherited {@link AllowRule} nodes are taken care of by examining the
   * visitor's current location when the target is actually found.
   */
  @Override
  public boolean visit(PackagePolicy x) {
    if (entity != null) {
      traverse(x.getPackagePolicies());
      traverse(x.getTypePolicies());
    }
    return false;
  }

  /**
   * Iterate over types when {@link #entity} is non-null, otherwise, just scan the global allows.
   */
  @Override
  public boolean visit(PolicyFile x) {
    if (entity == null) {
      traverse(x.getAllows());
    } else {
      traverse(x.getPackagePolicies());
      traverse(x.getTypePolicies());
    }
    return false;
  }

  /**
   * Determines whether or not to descend into a {@link PropertyPolicy} if {@link #property} is
   * non-null.
   */
  @Override
  public boolean visit(PropertyPolicy x) {
    for (PropertyList list : x.getPropertyLists()) {
      for (Ident<Property> ident : list.getPropertyNames()) {
        if (property.equals(ident.getReferent())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Determines whether or not to descend into a {@link TypePolicy} is {@link #entity} is non-null.
   * Also simplifies iteration if {@link #property} is null.
   */
  @Override
  public boolean visit(TypePolicy x) {
    if (entity == null || !entity.equals(x.getName().getReferent())) {
      return false;
    }
    // Cheat and look at our lexical scope to pick out package-scoped ACL rules
    for (PolicyNode n : currentLocation()) {
      if (n instanceof PackagePolicy) {
        traverse(((PackagePolicy) n).getAllows());
      }
    }
    traverse(x.getAllows());
    if (property != null) {
      traverse(x.getPolicies());
    }
    return false;
  }
}