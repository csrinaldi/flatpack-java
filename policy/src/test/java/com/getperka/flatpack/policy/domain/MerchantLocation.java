package com.getperka.flatpack.policy.domain;

import java.util.List;

import com.getperka.flatpack.BaseHasUuid;

public class MerchantLocation extends BaseHasUuid {
  private List<Clerk> clerks;
  private MerchantLocation templateLocation;
  private Merchant merchant;
  private String hidden;

  public List<Clerk> getClerks() {
    return clerks;
  }

  public String getHidden() {
    return hidden;
  }

  public Merchant getMerchant() {
    return merchant;
  }

  public MerchantLocation getTemplateLocation() {
    return templateLocation;
  }

  public void setClerks(List<Clerk> clerks) {
    this.clerks = clerks;
  }

  public void setHidden(String hidden) {
    this.hidden = hidden;
  }

  public void setMerchant(Merchant merchant) {
    this.merchant = merchant;
  }

  public void setTemplateLocation(MerchantLocation templateLocation) {
    this.templateLocation = templateLocation;
  }
}
