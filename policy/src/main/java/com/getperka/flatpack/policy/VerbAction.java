package com.getperka.flatpack.policy;

import com.getperka.flatpack.ext.SecurityAction;

public class VerbAction extends PolicyNode implements HasName<SecurityAction> {
  private Ident<SecurityAction> name;

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(name);
    }
    v.endVisit(this);
  }

  @Override
  public Ident<SecurityAction> getName() {
    return name;
  }

  @Override
  public void setName(Ident<SecurityAction> name) {
    this.name = name;
  }
}
