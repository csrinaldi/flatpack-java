package com.getperka.flatpack.policy;

import static com.getperka.flatpack.util.FlatPackCollections.setForIteration;

import java.util.Arrays;
import java.util.Set;

import org.junit.Test;
import org.parboiled.common.FileUtils;

import com.getperka.flatpack.Configuration;
import com.getperka.flatpack.FlatPack;
import com.getperka.flatpack.TypeSource;
import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.policy.domain.Clerk;
import com.getperka.flatpack.policy.domain.IntegratorUser;
import com.getperka.flatpack.policy.domain.IsPrincipalMapper;
import com.getperka.flatpack.policy.domain.Merchant;
import com.getperka.flatpack.policy.domain.MerchantLocation;
import com.getperka.flatpack.policy.domain.MerchantUser;

public class PolicyTest {

  @Test
  public void test() {
    String contents = FileUtils.readAllText(getClass().getResourceAsStream("test.policy"));
    StaticPolicy securityPolicy = new StaticPolicy(contents);
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
    flatpack.getTypeContext().extractProperties(Merchant.class);
    GroupPermissions p = securityPolicy.getPermissions(Merchant.class);
  }
}
