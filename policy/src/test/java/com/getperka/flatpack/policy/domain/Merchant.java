package com.getperka.flatpack.policy.domain;

import java.util.List;

import com.getperka.flatpack.BaseHasUuid;

public class Merchant extends BaseHasUuid {
  private String bar;
  private String foo;
  private String headline;
  private IntegratorUser integratorUser;
  private List<MerchantLocation> merchantLocations;
  private List<MerchantUser> merchantUsers;
  private String name;
  private String note;
  private String other;

  public String getBar() {
    return bar;
  }

  public String getFoo() {
    return foo;
  }

  public String getHeadline() {
    return headline;
  }

  public IntegratorUser getIntegratorUser() {
    return integratorUser;
  }

  public List<MerchantLocation> getMerchantLocations() {
    return merchantLocations;
  }

  public List<MerchantUser> getMerchantUsers() {
    return merchantUsers;
  }

  public String getName() {
    return name;
  }

  public String getNote() {
    return note;
  }

  public String getOther() {
    return other;
  }

  public void setBar(String bar) {
    this.bar = bar;
  }

  public void setFoo(String foo) {
    this.foo = foo;
  }

  public void setHeadline(String headline) {
    this.headline = headline;
  }

  public void setIntegratorUser(IntegratorUser integratorUser) {
    this.integratorUser = integratorUser;
  }

  public void setMerchantLocations(List<MerchantLocation> merchantLocations) {
    this.merchantLocations = merchantLocations;
  }

  public void setMerchantUsers(List<MerchantUser> merchantUsers) {
    this.merchantUsers = merchantUsers;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public void setOther(String other) {
    this.other = other;
  }
}
