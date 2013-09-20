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

import java.util.ArrayList;
import java.util.List;

import org.parboiled.Action;
import org.parboiled.BaseParser;
import org.parboiled.Context;
import org.parboiled.Parboiled;
import org.parboiled.Rule;
import org.parboiled.annotations.Cached;
import org.parboiled.annotations.DontExtend;
import org.parboiled.annotations.DontLabel;
import org.parboiled.annotations.Label;
import org.parboiled.support.StringVar;
import org.parboiled.support.Var;

import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.PropertyPath;
import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityGroup;
import com.getperka.flatpack.policy.pst.AclRule;
import com.getperka.flatpack.policy.pst.Allow;
import com.getperka.flatpack.policy.pst.Group;
import com.getperka.flatpack.policy.pst.GroupDefinition;
import com.getperka.flatpack.policy.pst.HasInheritFrom;
import com.getperka.flatpack.policy.pst.HasName;
import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PackagePolicy;
import com.getperka.flatpack.policy.pst.PolicyBlock;
import com.getperka.flatpack.policy.pst.PolicyFile;
import com.getperka.flatpack.policy.pst.PolicyNode;
import com.getperka.flatpack.policy.pst.PropertyList;
import com.getperka.flatpack.policy.pst.PropertyPolicy;
import com.getperka.flatpack.policy.pst.TypePolicy;
import com.getperka.flatpack.policy.pst.Verb;

/**
 * The grammar definition for the policy file.
 * <p>
 * The method names in this type use parboiled's <a
 * href="https://github.com/sirthias/parboiled/wiki/Style-Guide">naming scheme</a>, where methods
 * returning {@link Rule} objects are named with a capitalized first letter. Since most rules will
 * push an object onto the value stack, the rules are generally named in accordance with the object
 * type that they will push.
 */
class PolicyParser extends BaseParser<Object> {
  private static final String WILDCARD = "*";
  private static final PolicyParser parser = Parboiled.createParser(PolicyParser.class);

  /**
   * Return a new instance of a PolicyParser.
   */
  public static PolicyParser get() {
    return parser.newInstance();
  }

  /**
   * The top-level parse rule.
   */
  public Rule PolicyFile() {
    Var<PolicyFile> x = new Var<PolicyFile>(new PolicyFile());
    return Sequence(
        PolicyBlock(x),
        EOI);
  }

  /**
   * Tweak the value-stack push method to record the line number on which the current rule started.
   */
  @Override
  public boolean push(Object value) {
    if (value instanceof PolicyNode) {
      PolicyNode x = (PolicyNode) value;
      int startIndex = getContext().getStartIndex();
      x.setLineNumber(getContext().getInputBuffer().getPosition(startIndex).line);
    }
    return super.push(value);
  }

  /**
   * Makes string literals passed to Rule objects also consume any trailing whitespace.
   */
  @Override
  @DontExtend
  protected Rule fromStringLiteral(String string) {
    return Sequence(String(string), WS());
  }

  /**
   * An individual ACL rule:
   * 
   * <pre>
   * groupName none
   * 
   * groupName to verbName.actionName
   * 
   * inheritedProperty.groupName to ...
   * </pre>
   */
  Rule AclRule() {
    return Sequence(
        FirstOf(
            Sequence(
                Ident(Property.class),
                ".",
                Ident(SecurityGroup.class),
                new Action<Object>() {
                  @Override
                  public boolean run(Context<Object> ctx) {
                    Ident<SecurityGroup> group = popIdent(SecurityGroup.class);
                    Ident<Property> property = popIdent(Property.class);
                    push(new Ident<SecurityGroup>(SecurityGroup.class, property, group));
                    return true;
                  }
                }),
            WildcardOrIdent(SecurityGroup.class)),
        FirstOf(
            // Special syntax for a zero-length list
            Sequence("none", ACTION(push(new ArrayList<Ident<SecurityAction>>()))),
            // Or require at least one
            Sequence(
                "to",
                OneOrListOf(VerbActionOrWildcard(), Ident.class, ",")
            )
        ),
        new Action<Object>() {
          @Override
          public boolean run(Context<Object> ctx) {
            AclRule x = new AclRule();
            x.setSecurityActions(popIdentList(SecurityAction.class));
            x.setGroupName(popIdent(SecurityGroup.class));
            push(x);
            return true;
          }
        });
  }

  /**
   * An inheritable block which holds zero or more {@link AclRule}. Also supports a single-line
   * version.
   * 
   * <pre>
   * allow [ only ] [ inherit somePropertyName ] {
   *   aclRule;
   *   ...;
   *   aclRule;
   * }
   * </pre>
   * 
   * <pre>
   * allow [ inherit somePropertyName ] [ aclRule ];
   * </pre>
   */
  Rule Allow() {
    final Var<Allow> var = new Var<Allow>(new Allow());
    final Var<Boolean> only = new Var<Boolean>(false);
    return Sequence(
        "allow",
        Optional("only", ACTION(only.set(true))),
        MaybeInherit(Property.class, var),
        ZeroOneOrBlock(AclRule(), AclRule.class),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            Allow x = var.get();
            x.setAclRules((List<AclRule>) pop());
            x.setOnly(only.get());
            push(x);
            return true;
          }
        });
  }

  /**
   * Implement single-line and multi-line comments.
   */
  @DontLabel
  Rule Comment() {
    return FirstOf(
        Sequence(
            String("//"),
            ZeroOrMore(NoneOf(new char[] { '\n', '\r' })),
            WS()),
        Sequence(
            String("/*"),
            ZeroOrMore(
            FirstOf(
                NoneOf(WILDCARD),
                Sequence(String(WILDCARD), NoneOf("/")))),
            String("*/"),
            WS()));
  }

  @Cached
  <R, E> Rule CompoundIdent(final Class<R> referentType, final Class<E> elementType) {
    @SuppressWarnings("rawtypes")
    final Var<List<Ident>> parts = new Var<List<Ident>>(new ArrayList<Ident>());
    return Sequence(
        Ident(elementType),
        popToList(Ident.class, parts),
        ZeroOrMore(".", Ident(elementType), popToList(Ident.class, parts)),
        new Action<Object>() {
          @Override
          public boolean run(Context<Object> ctx) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            List<Ident<?>> list = (List) parts.get();
            if (list.size() == 1) {
              push(new Ident<R>(referentType, list.get(0).getSimpleName()));
            } else {
              push(new Ident<R>(referentType, list));
            }
            return true;
          }
        });
  }

  /**
   * An inheritable group which holds zero or more {@link GroupDefinition}.
   * 
   * <pre>
   * group [ inherit somePropertyName ] {
   *   groupDefinition;
   *   ...
   *   groupDefinition;
   * }
   * </pre>
   * 
   * <pre>
   * group [ inherit somePropertyName ] [ groupDefinition ];
   * </pre>
   */
  Rule Group() {
    final Var<Group> var = new Var<Group>(new Group());
    return Sequence(
        "group",
        MaybeInherit(Property.class, var),
        ZeroOneOrBlock(GroupDefinition(), GroupDefinition.class),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            Group x = var.get();
            x.setDefinitions((List<GroupDefinition>) pop());
            push(x);
            return true;
          }
        });
  }

  /**
   * Associates one or more {@link PropertyPath property paths} with a group name.
   * 
   * <pre>
   * groupName = some.property.path [ , another.path ]
   * </pre>
   */
  Rule GroupDefinition() {
    final Var<GroupDefinition> var = new Var<GroupDefinition>(new GroupDefinition());
    return Sequence(
        NodeName(SecurityGroup.class, var),
        "=",
        OneOrListOf(CompoundIdent(PropertyPath.class, Property.class), Ident.class, ","),
        new Action<Object>() {
          @Override
          public boolean run(Context<Object> ctx) {
            GroupDefinition x = var.get();
            x.setPaths(popIdentList(PropertyPath.class));
            push(x);
            return true;
          }
        });
  }

  /**
   * A lazy, by-name reference to another object. The syntax for these identifiers uses the same
   * rules as Java identifiers.
   */
  @Cached
  <R> Rule Ident(Class<R> referentType) {
    StringVar x = new StringVar();
    return Sequence(
        ANY,
        ACTION(Character.isJavaIdentifierStart(matchedChar()) && x.append(matchedChar())),
        ZeroOrMore(
            ANY,
            ACTION(Character.isJavaIdentifierPart(matchedChar()) && x.append(matchedChar()))
        ),
        ACTION(push(new Ident<R>(referentType, x.get()))),
        WS());
  }

  /**
   * Support rule to allow an optional {@code inherit ident} clause. If the inherit clause is
   * present, an {@link Ident} will be created and passed to
   * {@link HasInheritFrom#setInheritFrom(Ident) target}.
   */
  @Cached
  <P extends HasInheritFrom<R>, R> Rule MaybeInherit(final Class<R> clazz, final Var<P> target) {
    return Optional(
        "inherit",
        Ident(clazz),
        new Action<Object>() {
          @Override
          public boolean run(Context<Object> ctx) {
            target.get().setInheritFrom(popIdent(clazz));
            return true;
          }
        });
  }

  /**
   * Support rule to parse a single {@link Ident} and set {@code x}'s {@link HasName#setName(Ident)
   * name}.
   */
  @Cached
  <P extends HasName<R>, R> Rule NodeName(final Class<R> clazz, final Var<P> x) {
    return Sequence(
        FirstOf(
            Ident(clazz),
            ACTION(push(new Ident<R>(clazz, "$" + getContext().getPosition().line)))
        ),
        new Action<Object>() {
          @Override
          public boolean run(Context<Object> ctx) {
            Ident<R> ident = popIdent(clazz);
            P node = x.get();
            node.setName(ident);
            if (ident.getReferentType().isInstance(node)) {
              ident.setReferent(ident.getReferentType().cast(node));
            }
            return true;
          }
        });
  }

  /**
   * A hack when generics get in the way.
   */
  @Cached
  @SuppressWarnings({ "unchecked", "rawtypes" })
  Rule NodeNameRaw(Class clazz, final Var x) {
    return NodeName(clazz, x);
  }

  /**
   * Matches at least one instance of {@code r}, which must push exactly one value onto the stack.
   * The matched values will be added to a list, which will be placed on the stack.
   */
  @Cached
  <T extends PolicyNode> Rule OneOrListOf(Rule r, Class<T> clazz, String separator) {
    Var<List<T>> var = new Var<List<T>>(new ArrayList<T>());
    return Sequence(
        r,
        ACTION(popToList(clazz, var)),
        ZeroOrMore(
            separator,
            r,
            ACTION(popToList(clazz, var))),
        ACTION(clazz == null || push(var.get())));
  }

  Rule PackagePolicy() {
    Var<PackagePolicy> x = new Var<PackagePolicy>(new PackagePolicy());
    return Sequence(
        "package",
        NodeName(PackagePolicy.class, x),
        "{",
        PolicyBlock(x),
        "}");
  }

  /**
   * Main contents blocks.
   */
  @Cached
  Rule PolicyBlock(Var<? extends PolicyBlock> x) {
    return Sequence(
        WS(),
        ZeroOrMore(FirstOf(
            Sequence(Allow(), ACTION(x.get().getAllows().add((Allow) pop()))),
            Sequence(PackagePolicy(), ACTION(x.get().getPackagePolicies()
                .add((PackagePolicy) pop()))),
            Sequence(TypePolicy(), ACTION(x.get().getTypePolicies().add((TypePolicy) pop()))),
            Sequence(VerbDef(), ACTION(x.get().getVerbs().add((Verb) pop())))
        )),
        ACTION(push(x.get())));
  }

  <T> Ident<T> popIdent(Class<T> clazz) {
    return ((Ident<?>) pop()).cast(clazz);
  }

  <T> List<Ident<T>> popIdentList(Class<T> clazz) {
    @SuppressWarnings("unchecked")
    List<Ident<T>> toReturn = (List<Ident<T>>) pop();
    for (Ident<T> ident : toReturn) {
      ident.cast(clazz);
    }
    return toReturn;
  }

  /**
   * Utility method to pop a value from the value stack, cast it to {@code clazz} and add it to
   * {@code list}. if {@code clazz} is {@code null}, this method is a no-op.
   */
  <T> boolean popToList(Class<T> clazz, Var<List<T>> list) {
    if (clazz != null) {
      list.get().add(clazz.cast(pop()));
    }
    return true;
  }

  /**
   * One or more property-name references.
   * 
   * <pre>
   * property ident [ , ident [ ... ] ];
   * </pre>
   */
  Rule PropertyList() {
    return Sequence(
        "property",
        OneOrListOf(Ident(Property.class), Ident.class, ","),
        ";",
        new Action<Object>() {
          @Override
          public boolean run(Context<Object> ctx) {
            PropertyList x = new PropertyList();
            x.setPropertyNames(popIdentList(Property.class));
            push(x);
            return true;
          }
        });
  }

  /**
   * An named block that contains {@link Allow} and {@link PropertyList} nodes.
   * 
   * <pre>
   * policy name {
   *   property a, b, c;
   *   allow {
   *     ...
   *   }
   * }
   * </pre>
   */
  Rule PropertyPolicy() {
    Var<PropertyPolicy> x = new Var<PropertyPolicy>(new PropertyPolicy());
    return Sequence(
        "policy",
        NodeName(PropertyPolicy.class, x),
        "{",
        ZeroOrMore(FirstOf(
            Sequence(
                Allow(),
                ACTION(x.get().getAllows().add((Allow) pop()))),
            Sequence(
                PropertyList(),
                ACTION(x.get().getPropertyLists().add((PropertyList) pop())))
        )),
        "}",
        ACTION(push(x.get())));
  }

  /**
   * Defines policies for an entity type.
   * 
   * <pre>
   * type typeName {
   *   allow { ... }
   *   group { ... }
   *   policy { ... }
   *   verb ...;
   * }
   * </pre>
   */
  Rule TypePolicy() {
    final Var<TypePolicy> x = new Var<TypePolicy>(new TypePolicy());
    return Sequence(
        "type",
        NodeNameRaw(Class.class, x),
        "{",
        ZeroOrMore(FirstOf(
            Sequence(Allow(), ACTION(x.get().getAllows().add((Allow) pop()))),
            Sequence(Group(), ACTION(x.get().getGroups().add((Group) pop()))),
            Sequence(PropertyPolicy(), ACTION(x.get().getPolicies().add((PropertyPolicy) pop()))),
            Sequence(VerbDef(), ACTION(x.get().getVerbs().add((Verb) pop()))))),
        "}",
        ACTION(push(x.get())));
  }

  /**
   * A reference to an action. This may be a single {@link Ident} or a wildcard, possibly qualified
   * by a verb name.
   * 
   * <pre>
   * * 
   * *.*
   * Foo.bar
   * Foo.*
   * bar
   * </pre>
   */
  Rule VerbActionOrWildcard() {
    return FirstOf(
        Sequence(
            WILDCARD,
            Optional(".", WILDCARD),
            ACTION(push(new Ident<SecurityAction>(SecurityAction.class, "*")))),
        Sequence(
            Ident(Verb.class),
            ".",
            WildcardOrIdent(SecurityAction.class),
            ACTION(swap()
              && push(new Ident<SecurityAction>(SecurityAction.class, popIdent(Verb.class),
                  popIdent(SecurityAction.class))))),
        Ident(SecurityAction.class)

    );
  }

  /**
   * Defines an action verb.
   * 
   * <pre>
   * verb name = action, anotherAction, ...;
   * </pre>
   */
  Rule VerbDef() {
    final Var<Verb> var = new Var<Verb>(new Verb());
    return Sequence(
        "verb",
        NodeName(Verb.class, var),
        "=",
        OneOrListOf(Ident(SecurityAction.class), Ident.class, ","),
        ";",
        new Action<Object>() {
          @Override
          public boolean run(Context<Object> ctx) {
            Verb x = var.get();
            x.setActions(popIdentList(SecurityAction.class));
            push(x);
            return true;
          }
        });
  }

  /**
   * A single identifier or a wildcard.
   */
  <R> Rule WildcardOrIdent(Class<R> referentType) {
    return FirstOf(
        Sequence(WILDCARD, ACTION(push(new Ident<R>(referentType, WILDCARD)))),
        Ident(referentType));
  }

  /**
   * Matches whitespace or {@code #} comments.
   */
  @Label("whitespace")
  Rule WS() {
    return Sequence(
        ZeroOrMore(AnyOf(new char[] { ' ', '\n', '\r', '\t' })),
        Optional(Comment()));
  }

  /**
   * A utility rule to allow another rule to be specified zero, one, or any number of times.
   * 
   * <pre>
   * {
   *   rule;
   *   rule;
   *   ...
   * }
   * 
   * rule;
   * 
   * ;
   * </pre>
   */
  @Cached
  <T> Rule ZeroOneOrBlock(Rule r, Class<T> clazz) {
    Var<List<T>> var = new Var<List<T>>(new ArrayList<T>());
    return Sequence(
        FirstOf(
            Sequence(
                "{",
                ZeroOrMore(r, ";", ACTION(popToList(clazz, var))),
                "}"),
            Sequence(
                r,
                ";",
                ACTION(popToList(clazz, var))),
            ";"),
        ACTION(clazz == null || push(var.get())));
  }

}
