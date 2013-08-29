package com.getperka.flatpack.policy;

import java.util.ArrayList;
import java.util.Arrays;
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
  static class AclRule extends PolicyNode {
    Ident<Group> groupName;
    List<Ident<Verb>> verbNames = list();

    @Override
    public void accept(PolicyVisitor v) {
      if (v.visit(this)) {
        // No sub-nodes
      }
      v.endVisit(this);
    }
  }

  static class Allow extends PolicyNode implements HasInheritFrom<Allow> {
    List<AclRule> aclRules = list();
    Ident<Allow> inheritFrom;

    @Override
    public void accept(PolicyVisitor v) {
      if (v.visit(this)) {
        v.traverse(aclRules);
      }
      v.endVisit(this);
    }

    @Override
    public Ident<Allow> getInheritFrom() {
      return inheritFrom;
    }

    @Override
    public void setInheritFrom(Ident<Allow> inheritFrom) {
      this.inheritFrom = inheritFrom;
    }
  }

  static class Group extends PolicyNode implements HasInheritFrom<Group> {
    List<GroupDefinition> definitions = list();
    Ident<Group> inheritFrom;

    @Override
    public void accept(PolicyVisitor v) {
      if (v.visit(this)) {
        v.traverse(definitions);
      }
      v.endVisit(this);
    }

    @Override
    public Ident<Group> getInheritFrom() {
      return inheritFrom;
    }

    @Override
    public void setInheritFrom(Ident<Group> inheritFrom) {
      this.inheritFrom = inheritFrom;
    }
  }

  static class GroupDefinition extends PolicyNode implements HasName<GroupDefinition> {
    Ident<GroupDefinition> name;
    List<PropertyPath> paths = list();

    @Override
    public void accept(PolicyVisitor v) {
      if (v.visit(this)) {
        v.traverse(paths);
      }
      v.endVisit(this);
    }

    @Override
    public Ident<GroupDefinition> getName() {
      return name;
    }

    @Override
    public void setName(Ident<GroupDefinition> name) {
      this.name = name;
    }
  }

  interface HasInheritFrom<P extends PolicyNode & HasInheritFrom<P>> {
    public Ident<P> getInheritFrom();

    public void setInheritFrom(Ident<P> inheritFrom);
  }

  interface HasName<P extends PolicyNode & HasName<P>> {
    public Ident<P> getName();

    public void setName(Ident<P> name);
  }

  static class Ident<R> {
    List<Ident<?>> compoundName;
    String simpleName;
    R referent;

    public Ident(Ident<?>... compoundName) {
      this(Arrays.asList(compoundName));
    }

    public Ident(List<Ident<?>> compoundName) {
      this.compoundName = compoundName;
    }

    public Ident(String simpleName) {
      this.simpleName = simpleName;
    }

    public List<Ident<?>> getCompoundName() {
      return compoundName;
    }

    public R getReferent() {
      return referent;
    }

    public String getSimpleName() {
      return simpleName;
    }

    public boolean isCompound() {
      return compoundName != null;
    }

    public boolean isSimple() {
      return simpleName != null;
    }

    public boolean isWildcard() {
      return "*".equals(simpleName);
    }

    public void setReferent(R referent) {
      this.referent = referent;
    }
  }

  static class Policy extends PolicyNode implements HasInheritFrom<Policy>, HasName<Policy> {
    Ident<Policy> name;
    Ident<Policy> inheritFrom;
    List<Allow> allows = list();
    List<PropertyList> propertyLists = list();

    @Override
    public void accept(PolicyVisitor v) {
      if (v.visit(this)) {
        v.traverse(allows);
        v.traverse(propertyLists);
      }
      v.endVisit(this);
    }

    @Override
    public Ident<Policy> getInheritFrom() {
      return inheritFrom;
    }

    @Override
    public Ident<Policy> getName() {
      return name;
    }

    @Override
    public void setInheritFrom(Ident<Policy> inheritFrom) {
      this.inheritFrom = inheritFrom;
    }

    @Override
    public void setName(Ident<Policy> name) {
      this.name = name;
    }
  }

  static class PolicyFile extends PolicyNode {
    List<Allow> allows = list();
    List<Policy> policies = list();
    List<Verb> verbs = list();
    List<Type> types = list();

    @Override
    public void accept(PolicyVisitor v) {
      if (v.visit(this)) {
        v.traverse(allows);
        v.traverse(policies);
        v.traverse(verbs);
        v.traverse(types);
      }
      v.endVisit(this);
    }
  }

  static abstract class PolicyNode {
    public abstract void accept(PolicyVisitor v);
  }

  static class PolicyVisitor {
    public void endVisit(AclRule x) {}

    public void endVisit(Allow x) {}

    public void endVisit(Group x) {}

    public void endVisit(GroupDefinition x) {}

    public void endVisit(Policy x) {}

    public void endVisit(PolicyFile x) {}

    public void endVisit(PolicyNode x) {}

    public void endVisit(PropertyList x) {}

    public void endVisit(PropertyPath x) {}

    public void endVisit(Type x) {}

    public void endVisit(Verb x) {}

    public boolean visit(AclRule x) {
      return true;
    }

    public boolean visit(Allow x) {
      return true;
    }

    public boolean visit(Group x) {
      return true;
    }

    public boolean visit(GroupDefinition x) {
      return true;
    }

    public boolean visit(Policy x) {
      return true;
    }

    public boolean visit(PolicyFile x) {
      return true;
    }

    public boolean visit(PolicyNode x) {
      return true;
    }

    public boolean visit(PropertyList x) {
      return true;
    }

    public boolean visit(PropertyPath x) {
      return true;
    }

    public boolean visit(Type x) {
      return true;
    }

    public boolean visit(Verb x) {
      return true;
    }

    protected void traverse(List<? extends PolicyNode> list) {
      for (PolicyNode x : list) {
        x.accept(this);
      }
    }

    protected void traverse(PolicyNode x) {
      x.accept(this);
    }
  }

  static class PropertyList extends PolicyNode {
    List<Ident<?>> propertyNames = list();

    @Override
    public void accept(PolicyVisitor v) {
      if (v.visit(this)) {
        // No sub-nodes
      }
      v.endVisit(this);
    }
  }

  static class PropertyPath extends PolicyNode {
    List<Ident<?>> pathParts = list();

    @Override
    public void accept(PolicyVisitor v) {
      if (v.visit(this)) {
        // No sub-nodes
      }
      v.endVisit(this);
    }
  }

  static class Type extends PolicyNode implements HasName<Type> {
    Ident<Type> name;
    List<Allow> allow = list();
    List<Group> group = list();
    List<Policy> policy = list();
    List<Verb> verb = list();

    @Override
    public void accept(PolicyVisitor v) {
      if (v.visit(this)) {
        v.traverse(allow);
        v.traverse(group);
        v.traverse(policy);
        v.traverse(verb);
      }
      v.endVisit(this);
    }

    @Override
    public Ident<Type> getName() {
      return name;
    }

    @Override
    public void setName(Ident<Type> name) {
      this.name = name;
    }
  }

  static class Verb extends PolicyNode implements HasName<Verb> {
    Ident<Verb> name;
    List<Ident<?>> verbs = list();

    @Override
    public void accept(PolicyVisitor v) {
      if (v.visit(this)) {
        // No sub-nodes
      }
      v.endVisit(this);
    }

    @Override
    public Ident<Verb> getName() {
      return name;
    }

    @Override
    public void setName(Ident<Verb> name) {
      this.name = name;
    }
  }

  private static final PolicyParser parser = Parboiled.createParser(PolicyParser.class);

  public static PolicyParser get() {
    return parser.newInstance();
  }

  static <T> List<T> list() {
    return new ArrayList<T>();
  }

  public Rule PolicyFile() {
    Var<PolicyFile> x = new Var<PolicyFile>(new PolicyFile());
    return Sequence(
        WS(),
        ZeroOrMore(FirstOf(
            Sequence(Allow(), ACTION(x.get().allows.add((Allow) pop()))),
            Sequence(Policy(), ACTION(x.get().policies.add((Policy) pop()))),
            Sequence(VerbDef(), ACTION(x.get().verbs.add((Verb) pop()))),
            Sequence(Type(), ACTION(x.get().types.add((Type) pop())))
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
        OneOrListOf(VerbName(), Ident.class, ","),
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            AclRule x = new AclRule();
            x.verbNames = (List<Ident<Verb>>) pop();
            x.groupName = (Ident<Group>) pop();
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
            x.aclRules = (List<AclRule>) pop();
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
            x.definitions = (List<GroupDefinition>) pop();
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
            x.paths = (List<PropertyPath>) pop();
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

  Rule Policy() {
    Var<Policy> x = new Var<Policy>(new Policy());
    return Sequence(
        "policy",
        NodeName(x),
        MaybeInherit(x),
        OneOrBlock(
            FirstOf(
                Sequence(
                    Allow(),
                    ACTION(x.get().allows.add((Allow) pop()))),
                Sequence(
                    PropertyList(),
                    ACTION(x.get().propertyLists.add((PropertyList) pop())))
            ), null),
        ACTION(push(x.get())));
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
        new Action<Object>() {
          @Override
          @SuppressWarnings("unchecked")
          public boolean run(Context<Object> ctx) {
            PropertyList x = new PropertyList();
            x.propertyNames = (List<Ident<?>>) pop();
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
            x.pathParts = (List<Ident<?>>) pop();
            push(x);
            return true;
          }
        });
  }

  Rule Type() {
    Var<Type> x = new Var<Type>(new Type());
    return Sequence(
        "type",
        NodeName(x),
        "{",
        ZeroOrMore(FirstOf(
            Sequence(Allow(), ACTION(x.get().allow.add((Allow) pop()))),
            Sequence(Group(), ACTION(x.get().group.add((Group) pop()))),
            Sequence(Policy(), ACTION(x.get().policy.add((Policy) pop()))),
            Sequence(VerbDef(), ACTION(x.get().verb.add((Verb) pop()))))),
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
            x.verbs = (List<Ident<?>>) pop();
            push(x);
            return true;
          }
        });
  }

  Rule VerbName() {
    return FirstOf(
        Sequence(
            Ident(),
            ".",
            WildcardOrIdent(),
            ACTION(swap() && push(new Ident<Verb>((Ident<?>) pop(), (Ident<?>) pop())))),
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
