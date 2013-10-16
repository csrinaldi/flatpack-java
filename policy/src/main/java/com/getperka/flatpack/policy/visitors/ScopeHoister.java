package com.getperka.flatpack.policy.visitors;
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

import com.getperka.flatpack.policy.pst.AllowBlock;
import com.getperka.flatpack.policy.pst.PackagePolicy;
import com.getperka.flatpack.policy.pst.PolicyBlock;
import com.getperka.flatpack.policy.pst.PolicyFile;
import com.getperka.flatpack.policy.pst.PolicyNode;
import com.getperka.flatpack.policy.pst.TypePolicy;

/**
 * Hosts {@link AllowBlock} instances in a {@link PolicyBlock} into its enclosed {@link TypePolicy}
 * instances in order for the group names mentioned in the outer-level {@code allow} block to be
 * resolved against groups defined by the {@code type} block.
 */
public class ScopeHoister extends PolicyLocationVisitor {

  @Override
  public void endVisit(PackagePolicy x) {
    x.getAllows().clear();
  }

  @Override
  public void endVisit(PolicyFile x) {
    x.getAllows().clear();
  }

  @Override
  public void endVisit(TypePolicy x) {
    for (PolicyNode n : currentLocation()) {
      if (!(n instanceof PolicyBlock)) {
        continue;
      }
      PolicyBlock block = (PolicyBlock) n;
      List<AllowBlock> toCopy = block.getAllows();
      if (toCopy == null || toCopy.isEmpty()) {
        continue;
      }
      List<AllowBlock> copied = new PolicyCloner().clone(toCopy);
      x.getAllows().addAll(0, copied);
    }
  }

}
