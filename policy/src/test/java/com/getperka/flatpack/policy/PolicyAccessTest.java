package com.getperka.flatpack.policy;

import org.junit.Test;

import com.getperka.flatpack.FlatPack;

/**
 * Load the default policy and perform various checks against an actual data graph.
 */
public class PolicyAccessTest extends PolicyTestBase {

  @Test
  public void test() {
    FlatPack fp = flatpack(loadTestPolicyContents());

  }

}
