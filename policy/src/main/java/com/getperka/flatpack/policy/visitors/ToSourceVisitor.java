package com.getperka.flatpack.policy.visitors;

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

import java.util.List;

import com.getperka.flatpack.policy.pst.ActionDefinition;
import com.getperka.flatpack.policy.pst.AllowBlock;
import com.getperka.flatpack.policy.pst.AllowRule;
import com.getperka.flatpack.policy.pst.GroupBlock;
import com.getperka.flatpack.policy.pst.GroupDefinition;
import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PackagePolicy;
import com.getperka.flatpack.policy.pst.PolicyNode;
import com.getperka.flatpack.policy.pst.PropertyList;
import com.getperka.flatpack.policy.pst.PropertyPolicy;
import com.getperka.flatpack.policy.pst.TypePolicy;

/**
 * Generates an exquivalent policy source file from policy nodes.
 */
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
  public void traverse(List<? extends PolicyNode> list) {
    if (list == null) {
      return;
    }
    for (PolicyNode x : list) {
      traverse(x);
      nl();
    }
  }

  @Override
  public boolean visit(ActionDefinition x) {
    print("action ");
    traverse(x.getName());
    print(" = ");
    traverse(x.getActions(), ", ");
    println(";");
    return false;
  }

  @Override
  public boolean visit(AllowBlock x) {
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
  public boolean visit(AllowRule x) {
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
  public boolean visit(GroupBlock x) {
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
    if (x.getPaths().isEmpty()) {
      print("empty");
    } else {
      print(" = ");
      traverse(x.getPaths(), ", ");
    }
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
  public boolean visit(PackagePolicy x) {
    print("package ");
    traverse(x.getName());
    println(" {");
    indent();
    traverse(x.getAllows());
    traverse(x.getPackagePolicies());
    traverse(x.getTypePolicies());
    traverse(x.getVerbs());
    outdent();
    println("}");
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
