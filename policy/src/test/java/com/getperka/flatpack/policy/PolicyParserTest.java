package com.getperka.flatpack.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.parboiled.Rule;
import org.parboiled.buffers.DefaultInputBuffer;
import org.parboiled.common.FileUtils;
import org.parboiled.common.Predicates;
import org.parboiled.errors.ErrorUtils;
import org.parboiled.parserunners.ParseRunner;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.parserunners.TracingParseRunner;
import org.parboiled.support.Filters;
import org.parboiled.support.NodeFormatter;
import org.parboiled.support.ParsingResult;
import org.parboiled.trees.GraphUtils;

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

    // Test print-parse-print to make sure nothing is getting used
    String string = p.toString();
    System.out.println(string);
    PolicyFile p2 = (PolicyFile) testRule(parser.PolicyFile(), string);
    assertEquals(string, p2.toString());
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
    if (true) {
      TracingParseRunner<Object> trace = new TracingParseRunner<Object>(rule);
      trace.withFilter(Predicates.not(Filters.rules(parser.WS())));
      return trace;
    }

    return new ReportingParseRunner<Object>(rule);
  }

  private Object testRule(Rule rule, String input) {
    ParsingResult<Object> result = runner(rule).run(input);
    System.out.println(GraphUtils.printTree(result.parseTreeRoot, new NodeFormatter<Object>(
        new DefaultInputBuffer(
            input.toCharArray()))));
    checkResult(result);
    assertTrue(result.matched);
    return result.resultValue;
  }

}
