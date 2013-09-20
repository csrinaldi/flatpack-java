package com.getperka.flatpack.policy;

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

import javax.inject.Inject;
import javax.inject.Provider;

import org.parboiled.Rule;
import org.parboiled.errors.ErrorUtils;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;
import org.slf4j.Logger;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityGroup;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.SecurityTarget;
import com.getperka.flatpack.inject.FlatPackLogger;
import com.getperka.flatpack.policy.pst.AclRule;
import com.getperka.flatpack.policy.pst.Allow;
import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PackagePolicy;
import com.getperka.flatpack.policy.pst.PolicyFile;
import com.getperka.flatpack.policy.pst.PolicyNode;
import com.getperka.flatpack.policy.pst.PropertyList;
import com.getperka.flatpack.policy.pst.PropertyPolicy;
import com.getperka.flatpack.policy.pst.TypePolicy;
import com.getperka.flatpack.policy.visitors.IdentChecker;
import com.getperka.flatpack.policy.visitors.IdentResolver;
import com.getperka.flatpack.policy.visitors.PolicyLocationVisitor;

/**
 * Inner implementation of the static policy. This class does not provide any memoization of results
 * to avoid lifecycle requirements; caching is handled by the {@link StaticPolicy} implementation.
 */
class StaticPolicyImpl {
  static class PermissionsExtractor extends PolicyLocationVisitor {
    private final Class<? extends HasUuid> entity;
    private final Property property;
    private final GroupPermissions toReturn;

    public PermissionsExtractor(GroupPermissions toReturn, SecurityTarget target) {
      this.toReturn = toReturn;
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
     * Populates {@link #toReturn} with the actions in the rule.
     */
    @Override
    public boolean visit(AclRule x) {
      Set<SecurityAction> set = setForIteration();
      for (Ident<SecurityAction> ident : x.getSecurityActions()) {
        set.add(ident.getReferent());
      }
      SecurityGroup group = x.getGroupName().getReferent();
      toReturn.addPermissions(group, set);
      return false;
    }

    /**
     * Supports the "only" construct by clearing whatever permissions have already been accumulated.
     */
    @Override
    public boolean visit(Allow x) {
      if (x.isOnly()) {
        toReturn.clear();
      }
      return true;
    }

    /**
     * Descend to find types, the inherited {@link AclRule} nodes are taken care of by examining the
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
     * Determines whether or not to descend into a {@link TypePolicy} is {@link #entity} is
     * non-null. Also simplifies iteration if {@link #property} is null.
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

  @Inject
  private Provider<IdentChecker> checkers;
  @FlatPackLogger
  @Inject
  private Logger logger;
  private PolicyFile policy;
  @Inject
  private Provider<IdentResolver> resolvers;
  @Inject
  private SecurityGroups securityGroups;

  /**
   * Requires injection.
   */
  StaticPolicyImpl() {}

  public void extractPermissions(GroupPermissions accumulator, SecurityTarget target) {
    policy.accept(new PermissionsExtractor(accumulator, target));
  }

  public void parse(String contents) {
    Rule policyFile = PolicyParser.get().PolicyFile();
    ParsingResult<Object> result = new ReportingParseRunner<Object>(policyFile).run(contents);
    if (!result.parseErrors.isEmpty()) {
      throw new IllegalArgumentException(ErrorUtils.printParseErrors(result.parseErrors));
    }

    policy = (PolicyFile) result.resultValue;

    IdentResolver resolver = resolvers.get();
    resolver.exec(policy);
    if (!resolver.getErrors().isEmpty()) {
      StringBuilder sb = new StringBuilder("Could not resolve name(s):");
      for (String error : resolver.getErrors()) {
        sb.append("\n").append(error);
      }
      throw new IllegalArgumentException(sb.toString());
    }

    IdentChecker checker = checkers.get();
    policy.accept(checker);
    if (!checker.getErrors().isEmpty()) {
      throw new IllegalArgumentException(checker.getErrors().toString());
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Evaluated security policy:\n{}", policy.toSource());
    }
  }
}
