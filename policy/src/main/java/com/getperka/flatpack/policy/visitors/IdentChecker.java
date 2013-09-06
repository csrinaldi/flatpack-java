package com.getperka.flatpack.policy.visitors;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;

import java.util.List;

import com.getperka.flatpack.policy.pst.Allow;
import com.getperka.flatpack.policy.pst.Group;
import com.getperka.flatpack.policy.pst.Ident;

/**
 * Internal sanity checks to ensure that the policy has been processed correctly.
 */
public class IdentChecker extends PolicyLocationVisitor {

  private List<String> errors = listForAny();

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

  public List<String> getErrors() {
    return errors;
  }

  private void error(String message) {
    errors.add(summarizeLocation() + " " + message);
  }
}
