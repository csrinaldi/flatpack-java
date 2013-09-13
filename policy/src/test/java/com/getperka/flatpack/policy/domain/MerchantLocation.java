package com.getperka.flatpack.policy.domain;
/*
 * #%L
 * FlatPack Security Policy
 * %%
 * Copyright (C) 2012 - 2013 Perka Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
