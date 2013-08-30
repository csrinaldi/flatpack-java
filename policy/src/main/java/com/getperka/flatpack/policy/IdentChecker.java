package com.getperka.flatpack.policy;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;

import java.util.List;

public class IdentChecker extends PolicyLocationVisitor {

  private List<String> errors = listForAny();

  @Override
  public void endVisit(Ident<?> x) {
    if (x.getReferent() == null) {
      errors.add(summarizeLocation());
    }
    super.endVisit(x);
  }

  public List<String> getErrors() {
    return errors;
  }
}
