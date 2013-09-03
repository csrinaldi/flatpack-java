package com.getperka.flatpack.policy;

import static com.getperka.flatpack.util.FlatPackCollections.mapForIteration;
import static com.getperka.flatpack.util.FlatPackCollections.setForIteration;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Provider;

import org.parboiled.Rule;
import org.parboiled.errors.ErrorUtils;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityGroup;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.SecurityPolicy;
import com.getperka.flatpack.ext.TypeContext;

class StaticPolicyImpl implements SecurityPolicy {
  @Inject
  Provider<IdentChecker> checkers;
  @Inject
  Provider<IdentResolver> resolvers;
  @Inject
  SecurityGroups securityGroups;

  private PolicyFile policy;

  @Inject
  TypeContext typeContext;

  /**
   * Requires injection.
   */
  StaticPolicyImpl() {}

  @Override
  public GroupPermissions getDefaultPermissions() {
    return securityGroups.getPermissionsNone();
  }

  @Override
  public GroupPermissions getPermissions(final Class<? extends HasUuid> entity) {
    final AtomicReference<GroupPermissions> toReturn = new AtomicReference<GroupPermissions>();

    policy.accept(new PolicyVisitor() {
      private GroupPermissions p;

      @Override
      public void endVisit(TypePolicy x) {
        if (p != null) {
          p.setOperations(Collections.unmodifiableMap(p.getOperations()));
          toReturn.set(p);
          p = null;
        }
      }

      @Override
      public boolean visit(AclRule x) {
        // XXX Globals?
        extract(x, toReturn.get());
        return super.visit(x);
      }

      @Override
      public boolean visit(TypePolicy x) {
        if (!x.getName().getReferent().equals(entity)) {
          return false;
        }
        Map<SecurityGroup, Set<SecurityAction>> map = mapForIteration();
        p = new GroupPermissions();
        p.setOperations(map);
        return true;
      }
    });

    return toReturn.get();
  }

  @Override
  public GroupPermissions getPermissions(final Property property) {
    final Class<? extends HasUuid> entity = typeContext.getClass(property.getEnclosingTypeName());
    if (entity == null) {
      return null;
    }

    final AtomicReference<GroupPermissions> globalDefault = new AtomicReference<GroupPermissions>();
    final AtomicReference<GroupPermissions> typeDefault = new AtomicReference<GroupPermissions>();
    final AtomicReference<GroupPermissions> explicit = new AtomicReference<GroupPermissions>();
    policy.accept(new PolicyLocationVisitor() {

      /**
       * For each AclRule, see if it's a global default, type default, or explicit property block.
       */
      @Override
      public boolean visit(AclRule x) {
        PropertyPolicy propertyPolicy = currentLocation(PropertyPolicy.class);
        TypePolicy typePolicy = currentLocation(TypePolicy.class);
        if (propertyPolicy != null) {
          boolean found = false;
          for (PropertyList list : propertyPolicy.getPropertyLists()) {
            for (Ident<Property> ident : list.getPropertyNames()) {
              if (ident.getReferent().equals(property)) {
                found = true;
                break;
              }
            }
          }
          if (!found) {
            return false;
          }

          extract(x, explicit.get());
          return false;
        } else if (typePolicy != null) {
          extract(x, typeDefault.get());
        } else {
          extract(x, globalDefault.get());
        }
        return false;
      }

      /**
       * Ignore any TypePolicies unrelated to the property's enclosing type.
       */
      @Override
      public boolean visit(TypePolicy x) {
        return x.getName().getReferent().equals(entity);
      }
    });

    if (explicit.get() != null) {
      return explicit.get();
    }
    if (typeDefault.get() != null) {
      return typeDefault.get();
    }
    if (globalDefault.get() != null) {
      return globalDefault.get();
    }
    return null;
  }

  public void parse(String contents) {
    Rule policyFile = PolicyParser.get().PolicyFile();
    ParsingResult<Object> result = new ReportingParseRunner<Object>(policyFile).run(contents);
    if (!result.parseErrors.isEmpty()) {
      throw new IllegalArgumentException(ErrorUtils.printParseErrors(result.parseErrors));
    }

    policy = (PolicyFile) result.resultValue;
    System.out.println(policy.toString());

    IdentResolver resolver = resolvers.get();
    resolver.exec(policy);
    if (!resolver.getErrors().isEmpty()) {
      throw new IllegalArgumentException(resolver.getErrors().toString());
    }

    IdentChecker checker = checkers.get();
    policy.accept(checker);
    if (!checker.getErrors().isEmpty()) {
      throw new IllegalArgumentException(checker.getErrors().toString());
    }
  }

  private void extract(AclRule x, GroupPermissions p) {
    SecurityGroup group = x.getGroupName().getReferent();
    Set<SecurityAction> set = setForIteration();
    for (Ident<VerbAction> actionName : x.getVerbActions()) {
      if (actionName.isCompound()) {
        Ident<VerbAction> a = actionName.getCompoundName().get(1).cast(VerbAction.class);
        if (a.isWildcard()) {
          // CrudOperation.*
          Verb verb = actionName.getCompoundName().get(0).cast(Verb.class).getReferent();
          for (VerbAction toAdd : verb.getActions()) {
            set.add(new SecurityAction(verb.getName().getSimpleName(), toAdd.getName()
                .getSimpleName()));
          }
        } else {
          // CrudOperation.read
          set.add(a.getReferent().getName().getReferent());
        }
      } else {
        // read
        set.add(actionName.getReferent().getName().getReferent());
      }
    }
    p.getOperations().put(group, set);
  }
}
