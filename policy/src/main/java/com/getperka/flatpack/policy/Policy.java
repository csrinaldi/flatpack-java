package com.getperka.flatpack.policy;

import javax.inject.Inject;
import javax.inject.Provider;

import org.parboiled.Rule;
import org.parboiled.errors.ErrorUtils;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;

import com.getperka.flatpack.FlatPack;
import com.getperka.flatpack.inject.HasInjector;

public class Policy {
  public static Policy create(FlatPack flatpack) {
    return ((HasInjector) flatpack).getInjector().getInstance(Policy.class);
  }

  @Inject
  Provider<IdentChecker> checkers;
  @Inject
  Provider<IdentResolver> resolvers;

  /**
   * Requires injection.
   */
  Policy() {}

  public void parse(String policyContents) {
    Rule policyFile = PolicyParser.get().PolicyFile();
    ParsingResult<Object> result = new ReportingParseRunner<Object>(policyFile).run(policyContents);
    if (!result.parseErrors.isEmpty()) {
      throw new IllegalArgumentException(ErrorUtils.printParseErrors(result.parseErrors));
    }

    PolicyFile file = (PolicyFile) result.resultValue;
    System.out.println(file.toString());

    IdentResolver resolver = resolvers.get();
    file.accept(resolver);
    if (!resolver.getErrors().isEmpty()) {
      throw new IllegalArgumentException(resolver.getErrors().toString());
    }

    IdentChecker checker = checkers.get();
    file.accept(checker);
    if (!checker.getErrors().isEmpty()) {
      throw new IllegalArgumentException(checker.getErrors().toString());
    }
  }
}
