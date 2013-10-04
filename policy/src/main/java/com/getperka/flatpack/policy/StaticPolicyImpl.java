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



import javax.inject.Inject;
import javax.inject.Provider;

import org.parboiled.Rule;
import org.parboiled.errors.ErrorUtils;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;
import org.slf4j.Logger;

import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.SecurityTarget;
import com.getperka.flatpack.inject.FlatPackLogger;
import com.getperka.flatpack.policy.pst.PolicyFile;
import com.getperka.flatpack.policy.visitors.IdentChecker;
import com.getperka.flatpack.policy.visitors.IdentResolver;
import com.getperka.flatpack.policy.visitors.PermissionsExtractor;

/**
 * Inner implementation of the static policy. This class does not provide any memoization of results
 * to avoid lifecycle requirements; caching is handled by the {@link StaticPolicy} implementation.
 */
class StaticPolicyImpl {
  @Inject
  private Provider<IdentChecker> checkers;
  @FlatPackLogger
  @Inject
  private Logger logger;
  private PolicyFile policy;
  @Inject
  private Provider<IdentResolver> resolvers;
  @Inject
  private SecurityGroups securityGroups;

  /**
   * Requires injection.
   */
  StaticPolicyImpl() {}

  public void extractPermissions(GroupPermissions accumulator, SecurityTarget target) {
    policy.accept(new PermissionsExtractor(accumulator, target));
  }

  public void parse(String contents) {
    Rule policyFile = PolicyParser.get().PolicyFile();
    ParsingResult<Object> result = new ReportingParseRunner<Object>(policyFile).run(contents);
    if (!result.parseErrors.isEmpty()) {
      throw new IllegalArgumentException(ErrorUtils.printParseErrors(result.parseErrors));
    }

    policy = (PolicyFile) result.resultValue;

    IdentResolver resolver = resolvers.get();
    resolver.exec(policy);
    if (!resolver.getErrors().isEmpty()) {
      StringBuilder sb = new StringBuilder("Could not resolve name(s):");
      for (String error : resolver.getErrors()) {
        sb.append("\n").append(error);
      }
      throw new IllegalArgumentException(sb.toString());
    }

    IdentChecker checker = checkers.get();
    policy.accept(checker);
    if (!checker.getErrors().isEmpty()) {
      throw new IllegalArgumentException(checker.getErrors().toString());
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Evaluated security policy:\n{}", policy.toSource());
    }
  }
}
