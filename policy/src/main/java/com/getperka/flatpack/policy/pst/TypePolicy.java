package com.getperka.flatpack.policy.pst;

import java.util.List;

import com.getperka.flatpack.HasUuid;

public class TypePolicy extends PolicyNode implements HasName<Class<? extends HasUuid>> {
  private List<Allow> allows = list();
  private List<Group> groups = list();
  private Ident<Class<? extends HasUuid>> name;
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
  public Ident<Class<? extends HasUuid>> getName() {
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
  public void setName(Ident<Class<? extends HasUuid>> name) {
    this.name = name;
  }

  public void setPolicies(List<PropertyPolicy> policy) {
    this.policies = policy;
  }

  public void setVerbs(List<Verb> verb) {
    this.verbs = verb;
  }
}