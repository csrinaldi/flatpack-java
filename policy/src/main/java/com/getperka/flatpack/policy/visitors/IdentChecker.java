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
import static com.getperka.flatpack.util.FlatPackCollections.mapForLookup;

import java.util.List;
import java.util.Map;

import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.policy.pst.AllowBlock;
import com.getperka.flatpack.policy.pst.GroupBlock;
import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PropertyList;

/**
 * Internal sanity checks to ensure that the policy has been processed correctly.
 */
public class IdentChecker extends PolicyLocationVisitor {

  private List<String> errors = listForAny();
  private Map<Property, Integer> seenProperties = mapForLookup();

  /**
   * Requires injection.
   */
  IdentChecker() {}

  @Override
  public void endVisit(AllowBlock x) {
    if (x.getInheritFrom() != null) {
      error("Unexpanded inherit");
    }
  }

  @Override
  public void endVisit(GroupBlock x) {
    if (x.getInheritFrom() != null) {
      error("Unexpanded inherit");
    }
  }

  @Override
  public void endVisit(Ident<?> x) {
    if (x.getReferent() == null) {
      error("expecting a " + x.getReferentType().getSimpleName());
    }
  }

  /**
   * Ensure that a Property is not mentioned in more than one policy block.
   */
  @Override
  public void endVisit(PropertyList x) {
    for (Ident<Property> ident : x.getPropertyNames()) {
      Property seen = ident.getReferent();
      if (seen == null) {
        // Reported earlier
        continue;
      }
      Integer previouslySeenOnLine = seenProperties.put(seen, ident.getLineNumber());
      if (previouslySeenOnLine != null) {
        error("Property " + seen.getName() + " was already given a policy on line "
          + previouslySeenOnLine);
      }
    }
  }

  public List<String> getErrors() {
    return errors;
  }

  /**
   * Don't descend into complex Idents if a referent has already been established.
   */
  @Override
  public boolean visit(Ident<?> x) {
    if (x.isCompound() && x.getReferent() != null) {
      return false;
    }
    return true;
  }

  private void error(String message) {
    errors.add(summarizeLocation() + " " + message);
  }
}
