package com.getperka.flatpack.policy;

import java.util.List;

public class TypePolicy extends PolicyNode implements HasName<TypePolicy> {
  private List<Allow> allows = list();
  private List<Group> groups = list();
  private Ident<TypePolicy> name;
  private List<PropertyPolicy> policies = list();
  private List<Verb> verbs = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(allows);
      v.traverse(groups);
      v.traverse(name);
      v.traverse(policies);
      v.traverse(verbs);
    }
    v.endVisit(this);
  }

  public List<Allow> getAllows() {
    return allows;
  }

  public List<Group> getGroups() {
    return groups;
  }

  @Override
  public Ident<TypePolicy> getName() {
    return name;
  }

  public List<PropertyPolicy> getPolicies() {
    return policies;
  }

  public List<Verb> getVerbs() {
    return verbs;
  }

  public void setAllows(List<Allow> allow) {
    this.allows = allow;
  }

  public void setGroups(List<Group> group) {
    this.groups = group;
  }

  @Override
  public void setName(Ident<TypePolicy> name) {
    this.name = name;
  }

  public void setPolicies(List<PropertyPolicy> policy) {
    this.policies = policy;
  }

  public void setVerbs(List<Verb> verb) {
    this.verbs = verb;
  }
}