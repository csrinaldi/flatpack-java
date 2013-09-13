package com.getperka.flatpack.policy.visitors;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;
import static com.getperka.flatpack.util.FlatPackCollections.mapForLookup;

import java.util.List;
import java.util.Map;

import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.policy.pst.Allow;
import com.getperka.flatpack.policy.pst.Group;
import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PropertyList;

/**
 * Internal sanity checks to ensure that the policy has been processed correctly.
 */
public class IdentChecker extends PolicyLocationVisitor {

  private List<String> errors = listForAny();
  private Map<Property, Integer> seenProperties = mapForLookup();

  @Override
  public void endVisit(Allow x) {
    if (x.getInheritFrom() != null) {
      error("Unexpanded inherit");
    }
  }

  @Override
  public void endVisit(Group x) {
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

  private void error(String message) {
    errors.add(summarizeLocation() + " " + message);
  }
}
