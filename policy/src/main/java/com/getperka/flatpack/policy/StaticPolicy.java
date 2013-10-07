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
import static com.getperka.flatpack.util.FlatPackCollections.listForAny;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Provider;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.security.GroupPermissions;
import com.getperka.flatpack.security.SecurityGroups;
import com.getperka.flatpack.security.SecurityPolicy;
import com.getperka.flatpack.security.SecurityTarget;

/**
 * A {@link SecurityPolicy} implementation that uses policy rules defined in an external file.
 */
public class StaticPolicy implements SecurityPolicy {
  private final Map<SecurityTarget, GroupPermissions> cache =
      new ConcurrentHashMap<SecurityTarget, GroupPermissions>();
  private final String contents;
  private final AtomicReference<StaticPolicyImpl> impl = new AtomicReference<StaticPolicyImpl>();
  @Inject
  private Provider<StaticPolicyImpl> implProvider;
  @Inject
  private SecurityGroups securityGroups;
  @Inject
  private TypeContext typeContext;

  public StaticPolicy(String contents) {
    this.contents = contents;
  }

  /**
   * Computes all permissions for the target, including those inherited from supertype and global
   * decorations.
   */
  @Override
  public GroupPermissions getPermissions(SecurityTarget target) {
    /*
     * Since the policy file can't talk about specific entities, we'll generalize any incoming
     * target. This will allow targets to be cached, without indefinitely retaining an entity.
     */
    switch (target.getKind()) {
      case ENTITY:
        target = SecurityTarget.of(target.getEntityType());
        break;
      case ENTITY_PROPERTY:
        target = SecurityTarget.of(target.getProperty());
        break;
      case PROPERTY:
      case GLOBAL:
      case TYPE:
        // OK;
        break;
      default:
        // Definitively break if a new kind is added
        throw new UnsupportedOperationException(target.getKind().name());
    }

    // See if there's any cached data
    GroupPermissions toReturn = cache.get(target);
    if (toReturn != null) {
      return toReturn;
    }

    // Determine which targets should be inherited from
    List<SecurityTarget> targets = listForAny();
    computeTargets(target, targets);

    // Lazily construct the inner implementation, since it requires injection
    StaticPolicyImpl policy = maybeParse();

    // Pull the data from the policy tree
    toReturn = new GroupPermissions();
    for (SecurityTarget t : targets) {
      policy.extractPermissions(toReturn, t);
    }

    // Cache and return
    cache.put(target, toReturn);
    return toReturn;
  }

  /**
   * Returns {@link SecurityGroups#getPermissionsNone()}.
   */
  protected GroupPermissions getDefaultPermissions() {
    return securityGroups.getPermissionsNone();
  }

  private void computeTargets(SecurityTarget target, List<SecurityTarget> accumulator) {
    // Look for inherited data first
    switch (target.getKind()) {
      case GLOBAL:
        // Just add the global target
        break;
      case PROPERTY: {
        // Inherit from the entity type that defines the property
        Class<? extends HasUuid> enclosing =
            target.getProperty().getEnclosingType().getEntityType();
        computeTargets(SecurityTarget.of(enclosing), accumulator);
        break;
      }
      case TYPE: {
        // Inherit from the entity type's supertype or global rules
        Class<?> superType = target.getEntityType().getSuperclass();
        if (superType != null && HasUuid.class.isAssignableFrom(superType)) {
          computeTargets(SecurityTarget.of(superType.asSubclass(HasUuid.class)), accumulator);
        } else {
          computeTargets(SecurityTarget.global(), accumulator);
        }
        break;
      }
      case ENTITY:
      case ENTITY_PROPERTY:
        // Entity-based targets should have been generalized
      default:
        throw new UnsupportedOperationException(target.getKind().name());
    }
    accumulator.add(target);
  }

  private StaticPolicyImpl maybeParse() {
    StaticPolicyImpl toReturn = impl.get();
    if (toReturn != null) {
      return toReturn;
    }
    if (implProvider == null) {
      throw new IllegalStateException("Not injected, must be initialized via FlatPack.create()");
    }
    toReturn = implProvider.get();
    toReturn.parse(contents);
    impl.compareAndSet(null, toReturn);
    return impl.get();
  }
}
