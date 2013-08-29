package com.getperka.flatpack.policy;

import java.util.ArrayList;
import java.util.List;

public abstract class PolicyNode {
  /**
   * Convenience method for constructing a generic {@link ArrayList}.
   */
  protected static <T> List<T> list() {
    return new ArrayList<T>();
  }

  public abstract void accept(PolicyVisitor v);
}