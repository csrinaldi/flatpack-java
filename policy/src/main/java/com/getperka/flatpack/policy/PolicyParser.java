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
  private static final PolicyParser parser = Parboiled.createParser(PolicyParser.class);

  public static PolicyParser get() {
    return parser.newInstance();
  }

  public Rule PolicyFile() {
    Var<PolicyFile> x = new Var<PolicyFile>(new PolicyFile());
    return Sequence(
        WS(),
        ZeroOrMore(FirstOf(
            Sequence(Allow(), ACTION(x.get().getAllows().add((Allow) pop()))),
            Sequence(VerbDef(), ACTION(x.get().getVerbs().add((Verb) pop()))),
            Sequence(TypePolicy(), ACTION(x.get().getTypePolicies().add((TypePolicy) pop())))
        )),
        EOI,
        ACTION(push(x.get())));
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
        WildcardOrIdent(),
        "to",
        OneOrListOf(VerbIdent(), Ident.class, ","),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            AclRule x = new AclRule();
            x.setVerbNames((List<Ident<Verb>>) pop());
            x.setGroupName((Ident<Group>) pop());
            push(x);
            return true;
          }
        });
  }

  Rule Allow() {
    final Var<Allow> var = new Var<Allow>(new Allow());
    return Sequence(
        "allow",
        MaybeInherit(var),
        OneOrBlock(AclRule(), AclRule.class),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            Allow x = var.get();
            x.setAclRules((List<AclRule>) pop());
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
                NoneOf("*"),
                Sequence(String("*"), NoneOf("/")))),
            String("*/"),
            WS()));
  }

  Rule Group() {
    final Var<Group> var = new Var<Group>(new Group());
    return Sequence(
        "group",
        MaybeInherit(var),
        OneOrBlock(GroupDefinition(), GroupDefinition.class),
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

  Rule GroupDefinition() {
    final Var<GroupDefinition> var = new Var<GroupDefinition>(new GroupDefinition());
    return Sequence(
        NodeName(var),
        "=",
        OneOrListOf(PropertyPath(), PropertyPath.class, ","),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            GroupDefinition x = var.get();
            x.setPaths((List<PropertyPath>) pop());
            push(x);
            return true;
          }
        });
  }

  Rule Ident() {
    StringVar x = new StringVar();
    return Sequence(
        ANY,
        ACTION(Character.isJavaIdentifierStart(matchedChar()) && x.append(matchedChar())),
        ZeroOrMore(
            ANY,
            ACTION(Character.isJavaIdentifierPart(matchedChar()) && x.append(matchedChar()))
        ),
        ACTION(push(new Ident<PolicyNode>(x.get()))),
        WS());
  }

  @Cached
  <P extends PolicyNode & HasInheritFrom<P>> Rule MaybeInherit(final Var<P> target) {
    return Optional(
        "inherit",
        Ident(),
        new Action<Object>() {
          @Override
          public boolean run(Context<Object> ctx) {
            @SuppressWarnings("unchecked")
            Ident<P> ident = (Ident<P>) pop();
            target.get().setInheritFrom(ident);
            return true;
          }
        });
  }

  @Cached
  <P extends PolicyNode & HasName<P>> Rule NodeName(final Var<P> x) {
    return Sequence(
        Ident(),
        new Action<Object>() {
          @Override
          public boolean run(Context<Object> ctx) {
            @SuppressWarnings("unchecked")
            Ident<P> ident = (Ident<P>) pop();
            P node = x.get();
            node.setName(ident);
            ident.setReferent(node);
            return true;
          }
        });
  }

  @Cached
  <T> Rule OneOrBlock(Rule r, Class<T> clazz) {
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
                ACTION(popToList(clazz, var)))),
        ACTION(clazz == null || push(var.get())));
  }

  /**
   * Matches at least one instance of {@code r}, which must push exactly one value onto the stack.
   * The matched values will be added to a list, which will be placed on the stack.
   */
  @Cached
  <T> Rule OneOrListOf(Rule r, Class<T> clazz, String separator) {
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

  <T> boolean popToList(Class<T> clazz, Var<List<T>> list) {
    if (clazz != null) {
      list.get().add(clazz.cast(pop()));
    }
    return true;
  }

  Rule PropertyList() {
    return Sequence(
        "property",
        OneOrListOf(Ident(), Ident.class, ","),
        ";",
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            PropertyList x = new PropertyList();
            x.setPropertyNames((List<Ident<Object>>) pop());
            push(x);
            return true;
          }
        });
  }

  Rule PropertyPath() {
    return Sequence(
        OneOrListOf(Ident(), Ident.class, "."),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            PropertyPath x = new PropertyPath();
            x.setPathParts((List<Ident<Object>>) pop());
            push(x);
            return true;
          }
        });
  }

  Rule PropertyPolicy() {
    Var<PropertyPolicy> x = new Var<PropertyPolicy>(new PropertyPolicy());
    return Sequence(
        "policy",
        NodeName(x),
        MaybeInherit(x),
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

  Rule TypePolicy() {
    Var<TypePolicy> x = new Var<TypePolicy>(new TypePolicy());
    return Sequence(
        "type",
        NodeName(x),
        "{",
        ZeroOrMore(FirstOf(
            Sequence(Allow(), ACTION(x.get().getAllows().add((Allow) pop()))),
            Sequence(Group(), ACTION(x.get().getGroups().add((Group) pop()))),
            Sequence(PropertyPolicy(), ACTION(x.get().getPolicies().add((PropertyPolicy) pop()))),
            Sequence(VerbDef(), ACTION(x.get().getVerbs().add((Verb) pop()))))),
        "}",
        ACTION(push(x.get())));
  }

  Rule VerbDef() {
    final Var<Verb> var = new Var<Verb>(new Verb());
    return Sequence(
        "verb",
        NodeName(var),
        "=",
        OneOrListOf(Ident(), Ident.class, ","),
        ";",
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            Verb x = var.get();
            x.setVerbIdents((List<Ident<Object>>) pop());
            push(x);
            return true;
          }
        });
  }

  @SuppressWarnings("unchecked")
  Rule VerbIdent() {
    return FirstOf(
        Sequence(
            Ident(),
            ".",
            WildcardOrIdent(),
            ACTION(swap() && push(new Ident<Verb>((Ident<Object>) pop(), (Ident<Object>) pop())))),
        WildcardOrIdent());
  }

  <T> Rule WildcardOrIdent() {
    return FirstOf(
        Sequence("*", ACTION(push(new Ident<T>("*")))),
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
