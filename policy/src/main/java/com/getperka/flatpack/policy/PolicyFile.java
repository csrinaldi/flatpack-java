package com.getperka.flatpack.policy;

import java.util.List;

public class PolicyFile extends PolicyNode {
  private List<Allow> allows = list();
  private List<Policy> policies = list();
  private List<Verb> verbs = list();
  private List<Type> types = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(allows);
      v.traverse(policies);
      v.traverse(verbs);
      v.traverse(types);
    }
    v.endVisit(this);
  }

  public List<Allow> getAllows() {
    return allows;
  }

  public List<Policy> getPolicies() {
    return policies;
  }

  public List<Type> getTypes() {
    return types;
  }

  public List<Verb> getVerbs() {
    return verbs;
  }

  public void setAllows(List<Allow> allows) {
    this.allows = allows;
  }

  public void setPolicies(List<Policy> policies) {
    this.policies = policies;
  }

  public void setTypes(List<Type> types) {
    this.types = types;
  }

  public void setVerbs(List<Verb> verbs) {
    this.verbs = verbs;
  }
}