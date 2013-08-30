package com.getperka.flatpack.policy;

import org.junit.Test;
import org.parboiled.common.FileUtils;

import com.getperka.flatpack.Configuration;
import com.getperka.flatpack.FlatPack;

public class PolicyTest {

  @Test
  public void test() {
    String contents = FileUtils.readAllText(getClass().getResourceAsStream("test.policy"));
    FlatPack flatpack = FlatPack.create(new Configuration());
    Policy.create(flatpack).parse(contents);
  }
}
