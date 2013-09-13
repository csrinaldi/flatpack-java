package com.getperka.flatpack.policy.pst;

import java.util.ArrayList;
import java.util.List;

import com.getperka.flatpack.policy.visitors.ToSourceVisitor;
import com.getperka.flatpack.policy.visitors.ToStringVisitor;

public abstract class PolicyNode {
  /**
   * Convenience method for constructing a generic {@link ArrayList}.
   */
  protected static <T> List<T> list() {
    return new ArrayList<T>();
  }

  private int lineNumber = -1;

  public abstract void accept(PolicyVisitor v);

  public int getLineNumber() {
    return lineNumber;
  }

  public void setLineNumber(int lineNumber) {
    this.lineNumber = lineNumber;
  }

  /**
   * Returns a canonical representation of the PolicyNode.
   */
  public String toSource() {
    return toString(new ToSourceVisitor());
  }

  /**
   * Returns a summary of the PolicyNode. For debugging use only.
   */
  @Override
  public String toString() {
    return toString(new ToStringVisitor());
  }

  private String toString(PolicyVisitor v) {
    accept(v);
    return v.toString();
  }
}