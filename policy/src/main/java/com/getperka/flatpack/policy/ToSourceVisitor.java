package com.getperka.flatpack.policy;

import java.util.List;

import com.getperka.flatpack.policy.pst.AclRule;
import com.getperka.flatpack.policy.pst.Allow;
import com.getperka.flatpack.policy.pst.Group;
import com.getperka.flatpack.policy.pst.GroupDefinition;
import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PolicyFile;
import com.getperka.flatpack.policy.pst.PolicyNode;
import com.getperka.flatpack.policy.pst.PolicyVisitor;
import com.getperka.flatpack.policy.pst.PropertyList;
import com.getperka.flatpack.policy.pst.PropertyPolicy;
import com.getperka.flatpack.policy.pst.TypePolicy;
import com.getperka.flatpack.policy.pst.Verb;

public class ToSourceVisitor extends PolicyVisitor {
  private int indent;
  private boolean needsIndent = true;
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
    if (x.getSecurityActions().isEmpty()) {
      print(" none");
      return false;
    }
    print(" to ");
    traverse(x.getSecurityActions(), ", ");
    return false;
  }

  @Override
  public boolean visit(Allow x) {
    print("allow ");
    if (x.isOnly()) {
      print("only ");
    }
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
    traverse(x.getVerbs());
    traverse(x.getAllows());
    traverse(x.getTypePolicies());
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
    traverse(x.getVerbs());
    traverse(x.getGroups());
    traverse(x.getAllows());
    traverse(x.getPolicies());
    closeBlock();
    return false;
  }

  @Override
  public boolean visit(Verb x) {
    print("verb ");
    traverse(x.getName());
    print(" = ");
    traverse(x.getActions(), ", ");
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

  protected void nl() {
    // Don't print empty lines
    if (needsIndent) {
      return;
    }
    print("\n");
    needsIndent = true;
  }

  protected void openBlock() {
    println("{");
    indent();
  }

  protected void outdent() {
    indent = Math.max(0, indent - 1);
  }

  protected void print(String data) {
    if (needsIndent) {
      for (int i = 0; i < indent; i++) {
        sb.append("  ");
      }
      needsIndent = false;
    }
    sb.append(data);
  }

  protected void print(String... data) {
    for (String s : data) {
      print(s);
    }
  }

  protected void printBlockOrSingleton(List<? extends PolicyNode> list) {
    if (list.size() == 0) {
      println(";");
      return;
    }
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
    nl();
  }

  @Override
  protected void traverse(List<? extends PolicyNode> list) {
    if (list == null) {
      return;
    }
    for (PolicyNode x : list) {
      traverse(x);
      nl();
    }
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
