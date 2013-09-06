package com.getperka.flatpack.policy.visitors;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.getperka.flatpack.policy.pst.PolicyNode;
import com.getperka.flatpack.policy.pst.PolicyVisitor;

/**
 * A utility class for policy visitors that maintains the current location of the visitor to improve
 * error messages.
 */
public class PolicyLocationVisitor extends PolicyVisitor {

  private final Deque<PolicyNode> location = new ArrayDeque<PolicyNode>();

  protected List<PolicyNode> currentLocation() {
    List<PolicyNode> toReturn = listForAny();
    toReturn.addAll(location);
    return toReturn;
  }

  protected <P extends PolicyNode> P currentLocation(Class<P> clazz) {
    for (PolicyNode x : location) {
      if (clazz.isInstance(x)) {
        return clazz.cast(x);
      }
    }
    return null;
  }

  protected void doTraverse(PolicyNode x) {
    super.traverse(x);
  }

  protected String summarizeLocation() {
    if (location.isEmpty()) {
      return null;
    }
    PolicyNode node = location.peek();

    String toReturn = node.toString();

    if (node.getLineNumber() == -1) {
      toReturn += " (Unknown line)";
    } else {
      toReturn += " (Line " + node.getLineNumber() + ")";
    }
    return toReturn;
  }

  @Override
  protected final void traverse(PolicyNode x) {
    if (x != null) {
      location.push(x);
    }
    try {
      doTraverse(x);
    } finally {
      if (x != null) {
        location.pop();
      }
    }
  }
}
