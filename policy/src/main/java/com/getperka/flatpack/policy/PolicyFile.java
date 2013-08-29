package com.getperka.flatpack.policy;

import java.util.List;

public class PolicyFile extends PolicyNode {
  private List<Allow> allows = list();
  private List<Verb> verbs = list();
  private List<TypePolicy> typePolicies = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(allows);
      v.traverse(verbs);
      v.traverse(typePolicies);
    }
    v.endVisit(this);
  }

  public List<Allow> getAllows() {
    return allows;
  }

  public List<TypePolicy> getTypePolicies() {
    return typePolicies;
  }

  public List<Verb> getVerbs() {
    return verbs;
  }

  public void setAllows(List<Allow> allows) {
    this.allows = allows;
  }

  public void setTypePolicies(List<TypePolicy> types) {
    this.typePolicies = types;
  }

  public void setVerbs(List<Verb> verbs) {
    this.verbs = verbs;
  }
}