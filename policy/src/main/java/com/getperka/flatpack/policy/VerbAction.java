package com.getperka.flatpack.policy;

public class VerbAction extends PolicyNode implements HasName<VerbAction> {
  private Ident<VerbAction> name;

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(name);
    }
    v.endVisit(this);
  }

  @Override
  public Ident<VerbAction> getName() {
    return name;
  }

  @Override
  public void setName(Ident<VerbAction> name) {
    this.name = name;
  }
}
