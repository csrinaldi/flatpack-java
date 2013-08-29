package com.getperka.flatpack.policy;

import java.util.List;

public class ToStringVisitor extends PolicyVisitor {
  private boolean needsIndent;
  private int indent;
  private final StringBuilder sb = new StringBuilder();

  public boolean print(Ident<?> x) {
    if (x.isSimple()) {
      print(x.getSimpleName());
    } else if (x.isCompound()) {
      print(x.getCompoundName().get(0));
      for (int i = 0, j = x.getCompoundName().size(); i < j; i++) {
        print(".");
        print(x.getCompoundName().get(i));
      }
    } else {
      throw new UnsupportedOperationException();
    }
    return false;
  }

  @Override
  public String toString() {
    return sb.toString();
  }

  @Override
  public boolean visit(AclRule x) {
    traverse(x.getGroupName());
    print(" to ");
    traverse(x.getVerbNames(), ", ");
    return false;
  }

  @Override
  public boolean visit(Allow x) {
    print("allow ");
    if (x.getInheritFrom() != null) {
      print("inherit ");
      traverse(x.getInheritFrom());
      print(" ");
    }
    printBlockOrSingleton(x.getAclRules());
    return false;
  }

  @Override
  public boolean visit(Group x) {
    print("group ");
    if (x.getInheritFrom() != null) {
      print("inherit ");
      traverse(x.getInheritFrom());
      print(" ");
    }
    printBlockOrSingleton(x.getDefinitions());
    return false;
  }

  @Override
  public boolean visit(GroupDefinition x) {
    traverse(x.getName());
    print(" = ");
    traverse(x.getPaths(), ", ");
    return false;
  }

  @Override
  public boolean visit(Ident<?> x) {
    if (x.isSimple()) {
      print(x.getSimpleName());
    } else if (x.isCompound()) {
      traverse(x.getCompoundName(), ".");
    } else {
      print("<NULL>");
    }
    return false;
  }

  @Override
  public boolean visit(PolicyFile x) {
    traverse(x.getVerbs(), "\n");
    traverse(x.getAllows(), "\n");
    traverse(x.getTypePolicies(), "\n");
    return false;
  }

  @Override
  public boolean visit(PropertyList x) {
    print("property ");
    traverse(x.getPropertyNames(), ", ");
    println(";");
    return false;
  }

  @Override
  public boolean visit(PropertyPath x) {
    traverse(x.getPathParts(), ".");
    return false;
  }

  @Override
  public boolean visit(PropertyPolicy x) {
    print("policy ");
    traverse(x.getName());
    print(" ");
    openBlock();
    traverse(x.getPropertyLists());
    traverse(x.getAllows());
    closeBlock();
    return false;
  }

  @Override
  public boolean visit(TypePolicy x) {
    print("type ");
    traverse(x.getName());
    print(" ");
    openBlock();
    traverse(x.getVerbs(), "\n");
    traverse(x.getGroups(), "\n");
    traverse(x.getAllows(), "\n");
    traverse(x.getPolicies(), "\n");
    closeBlock();
    return false;
  }

  @Override
  public boolean visit(Verb x) {
    print("verb ");
    traverse(x.getName());
    print(" = ");
    traverse(x.getVerbIdents(), ", ");
    println(";");
    return false;
  }

  protected void closeBlock() {
    outdent();
    println("}");
  }

  protected void indent() {
    indent++;
  }

  protected void openBlock() {
    println("{");
    indent();
  }

  protected void outdent() {
    indent = Math.max(0, indent - 1);
  }

  protected void print(String data) {
    for (String s : data.split("\n")) {
      if (needsIndent) {
        for (int i = 0; i < indent; i++) {
          sb.append("  ");
        }
        needsIndent = false;
      }
      sb.append(s);
    }
  }

  protected void print(String... data) {
    for (String s : data) {
      print(s);
    }
  }

  protected void printBlockOrSingleton(List<? extends PolicyNode> list) {
    if (list.size() == 1) {
      traverse(list.get(0));
      println(";");
      return;
    }
    openBlock();
    for (PolicyNode x : list) {
      traverse(x);
      println(";");
    }
    closeBlock();
  }

  protected void println(String... data) {
    print(data);
    sb.append("\n");
    needsIndent = true;
  }

  protected void traverse(List<? extends PolicyNode> list, String separator) {
    boolean needsSeparator = false;
    for (PolicyNode x : list) {
      if (needsSeparator) {
        print(separator);
      } else {
        needsSeparator = true;
      }
      traverse(x);
    }
  }
}
