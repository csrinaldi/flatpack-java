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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.parboiled.Rule;
import org.parboiled.common.FileUtils;
import org.parboiled.common.Predicates;
import org.parboiled.errors.ErrorUtils;
import org.parboiled.parserunners.ParseRunner;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.parserunners.TracingParseRunner;
import org.parboiled.support.Filters;
import org.parboiled.support.ParsingResult;

import com.getperka.flatpack.policy.pst.PolicyFile;
import com.getperka.flatpack.policy.visitors.PolicyCloner;

public class PolicyParserTest {
  private PolicyParser parser;

  @Before
  public void before() {
    parser = PolicyParser.get();
  }

  @Test
  public void test() throws IOException {
    String contents = FileUtils.readAllText(getClass().getResourceAsStream("test.policy"));
    PolicyFile p = (PolicyFile) testRule(parser.PolicyFile(), contents);

    // Test print-parse-print to make sure nothing is getting lost
    String string = p.toSource();
    PolicyFile p2 = (PolicyFile) testRule(parser.PolicyFile(), string);
    assertEquals(string, p2.toSource());
  }

  @Test
  public void testCloner() throws IOException {
    String contents = FileUtils.readAllText(getClass().getResourceAsStream("test.policy"));
    PolicyFile p = (PolicyFile) testRule(parser.PolicyFile(), contents);
    PolicyFile p2 = new PolicyCloner().clone(p);

    assertEquals(p.toSource(), p2.toSource());
  }

  private void checkResult(ParsingResult<Object> res) {
    if (res.parseErrors.isEmpty()) {
      assertTrue(res.matched);
      return;
    }
    fail(ErrorUtils.printParseErrors(res.parseErrors));
  }

  @SuppressWarnings("unused")
  private ParseRunner<Object> runner(Rule rule) {
    // Enable to turn on lots of parsing spam
    if (false) {
      TracingParseRunner<Object> trace = new TracingParseRunner<Object>(rule);
      trace.withFilter(Predicates.not(Filters.rules(parser.WS())));
      return trace;
    }

    return new ReportingParseRunner<Object>(rule);
  }

  private Object testRule(Rule rule, String input) {
    ParsingResult<Object> result = runner(rule).run(input);
    checkResult(result);
    assertTrue(result.matched);
    return result.resultValue;
  }

}
