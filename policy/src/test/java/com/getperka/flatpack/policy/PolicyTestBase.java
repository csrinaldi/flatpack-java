package com.getperka.flatpack.policy;
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

import static com.getperka.flatpack.util.FlatPackCollections.setForIteration;

import java.util.Arrays;
import java.util.Set;

import org.parboiled.common.FileUtils;
import org.parboiled.parserunners.BasicParseRunner;

import com.getperka.flatpack.Configuration;
import com.getperka.flatpack.FlatPack;
import com.getperka.flatpack.TypeSource;
import com.getperka.flatpack.policy.domain.Clerk;
import com.getperka.flatpack.policy.domain.IntegratorUser;
import com.getperka.flatpack.policy.domain.IsPrincipalMapper;
import com.getperka.flatpack.policy.domain.Merchant;
import com.getperka.flatpack.policy.domain.MerchantLocation;
import com.getperka.flatpack.policy.domain.MerchantUser;
import com.getperka.flatpack.policy.pst.PolicyFile;

public class PolicyTestBase {

  protected FlatPack flatpack(String policyContents) {
    StaticPolicy securityPolicy = new StaticPolicy(policyContents);
    FlatPack flatpack = FlatPack.create(
        new Configuration()
            .addTypeSource(new TypeSource() {
              @Override
              public Set<Class<?>> getTypes() {
                Set<Class<?>> toReturn = setForIteration();
                toReturn.addAll(Arrays.<Class<?>> asList(
                    Clerk.class, IntegratorUser.class, Merchant.class,
                    MerchantLocation.class, MerchantUser.class));
                return toReturn;
              }
            })
            .withPrincipalMapper(new IsPrincipalMapper())
            .withSecurityPolicy(securityPolicy));
    return flatpack;
  }

  protected PolicyFile loadTestPolicy() {
    return parsePolicy(loadTestPolicyContents());
  }

  protected String loadTestPolicyContents() {
    return loadTestPolicyContents("test.policy");
  }

  protected String loadTestPolicyContents(String resourceName) {
    return FileUtils.readAllText(getClass().getResourceAsStream(resourceName));
  }

  protected PolicyFile parsePolicy(String contents) {
    BasicParseRunner<Object> runner = new BasicParseRunner<Object>(PolicyParser.get().PolicyFile());
    return (PolicyFile) runner.run(contents).resultValue;
  }
}
