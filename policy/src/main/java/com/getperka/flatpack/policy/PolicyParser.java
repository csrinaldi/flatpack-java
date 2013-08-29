package com.getperka.flatpack.policy;

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

public class PolicyParser extends BaseParser<Object> {
  class AclRule {
    String groupName;
    List<String> verbNames;
  }

  class Allow {
    List<AclRule> aclRules;
    String inheritFrom;
  }

  class Group {
    List<GroupDefinition> definitions;
    String inheritFrom;
  }

  class GroupDefinition {
    String name;
    List<PropertyPath> paths;
  }

  class Policy {
    String name;
    String inheritFrom;
    List<Allow> allows;
    List<PropertyList> propertyLists;
  }

  class PropertyList {
    List<String> propertyNames;
  }

  class PropertyPath {
    List<String> pathParts;
  }

  class Type {
    String name;
    List<Allow> allow;
    List<Group> group;
    List<Policy> policy;
    List<Verb> verb;
  }

  class Verb {
    String name;
    List<String> verbs;
  }

  private static final PolicyParser parser = Parboiled.createParser(PolicyParser.class);

  public static PolicyParser get() {
    return parser.newInstance();
  }

  public Rule PolicyFile() {
    return Sequence(
        WS(),
        ZeroOrMore(FirstOf(
            Allow(),
            Policy(),
            VerbDef(),
            Type()
        )),
        EOI);
  }

  /**
   * Consumes whitespace.
   */
  @Override
  @DontExtend
  protected Rule fromStringLiteral(String string) {
    return Sequence(String(string), WS());
  }

  Rule AclRule() {
    return Sequence(
        FirstOf("*", Ident()),
        "to",
        OneOrListOf(VerbName(), ","),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            AclRule x = new AclRule();
            x.verbNames = (List<java.lang.String>) pop();
            x.groupName = (String) pop();
            push(x);
            return true;
          }
        });
  }

  Rule Allow() {
    final StringVar inheritFrom = new StringVar();
    return Sequence(
        "allow",
        MaybeInherit(inheritFrom),
        OneOrBlock(AclRule()),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            Allow x = new Allow();
            x.aclRules = (List<com.getperka.flatpack.policy.PolicyParser.AclRule>) pop();
            x.inheritFrom = inheritFrom.get();
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
                NoneOf("*"),
                Sequence(String("*"), NoneOf("/")))),
            String("*/"),
            WS()));
  }

  Rule Group() {
    final StringVar inherit = new StringVar();
    return Sequence(
        "group",
        MaybeInherit(inherit),
        OneOrBlock(GroupDefinition()),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            Group x = new Group();
            x.definitions = (List<com.getperka.flatpack.policy.PolicyParser.GroupDefinition>) pop();
            x.inheritFrom = inherit.get();
            return true;
          }
        });
  }

  Rule GroupDefinition() {
    return Sequence(
        Ident(),
        "=",
        OneOrListOf(PropertyPath(), ","),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            GroupDefinition x = new GroupDefinition();
            x.paths = (List<PropertyPath>) pop();
            x.name = (java.lang.String) pop();
            return true;
          }
        });
  }

  Rule Ident() {
    return Sequence(
        ANY,
        ACTION(Character.isJavaIdentifierStart(matchedChar())),
        ZeroOrMore(
            ANY,
            ACTION(Character.isJavaIdentifierPart(matchedChar()))
        ),
        ACTION(push(getContext().getMatch())),
        WS());
  }

  @Cached
  Rule MaybeInherit(StringVar target) {
    return Optional("inherit", Ident(), target.set((String) pop()));
  }

  @Cached
  Rule OneOrBlock(Rule r) {
    Var<List<Object>> var = new Var<List<Object>>(new ArrayList<Object>());
    return Sequence(
        FirstOf(
            Sequence("{", ZeroOrMore(r, ";", popToList(var)), "}"),
            Sequence(r, popToList(var))),
        push(var.get()));
  }

  /**
   * Matches at least one instance of {@code r}, which must push exactly one value onto the stack.
   * The matched values will be added to a list, which will be placed on the stack.
   */
  @Cached
  Rule OneOrListOf(Rule r, String separator) {
    Var<List<Object>> var = new Var<List<Object>>(new ArrayList<Object>());
    return Sequence(
        r,
        ACTION(popToList(var)),
        ZeroOrMore(
            separator,
            r,
            ACTION(popToList(var))),
        ACTION(push(var.get())));
  }

  Rule Policy() {
    Var<Policy> x = new Var<Policy>(new Policy());
    return Sequence(
        "policy",
        Ident(),
        ACTION((x.get().name = (String) pop()) != null),
        Optional("inherit", Ident(), ACTION((x.get().inheritFrom = (String) pop()) != null)),
        OneOrBlock(
        FirstOf(
            Sequence(Allow(), ACTION(x.get().allows.add((Allow) pop()))),
            Sequence(PropertyList(), ACTION(x.get().propertyLists.add((PropertyList) pop())))
        )));
  }

  @SuppressWarnings("unchecked")
  <T> boolean popToList(Var<List<T>> list) {
    list.get().add((T) pop());
    return true;
  }

  Rule PropertyList() {
    return Sequence(
        "property",
        OneOrListOf(Ident(), ","),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            PropertyList x = new PropertyList();
            x.propertyNames = (List<java.lang.String>) pop();
            push(x);
            return true;
          }
        });
  }

  Rule PropertyPath() {
    return Sequence(
        OneOrListOf(Ident(), "."),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            PropertyPath x = new PropertyPath();
            x.pathParts = (List<java.lang.String>) pop();
            push(x);
            return true;
          }
        });
  }

  Rule Type() {
    Var<Type> x = new Var<Type>(new Type());
    return Sequence(
        "type",
        Ident(),
        ACTION((x.get().name = (String) pop()) != null),
        "{",
        ZeroOrMore(FirstOf(
            Sequence(Allow(), ACTION(x.get().allow.add((Allow) pop()))),
            Sequence(Group(), ACTION(x.get().group.add((Group) pop()))),
            Sequence(Policy(), ACTION(x.get().policy.add((Policy) pop()))),
            Sequence(VerbDef(), ACTION(x.get().verb.add((Verb) pop()))))),
        "}");
  }

  Rule VerbDef() {
    return Sequence(
        "verb",
        Ident(),
        "=",
        OneOrListOf(Ident(), ","),
        ";",
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            Verb x = new Verb();
            x.verbs = (List<java.lang.String>) pop();
            x.name = (java.lang.String) pop();
            return true;
          }
        });
  }

  Rule VerbName() {
    return FirstOf(
        "*",
        Sequence(Ident(), ".", FirstOf("*", Ident())),
        Ident());
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

}
