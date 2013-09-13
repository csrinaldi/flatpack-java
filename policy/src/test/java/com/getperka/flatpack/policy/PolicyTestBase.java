package com.getperka.flatpack.policy;

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
