package com.github.sommeri.less4j.core.compiler.scopes;

import java.util.HashMap;
import java.util.Map;

import com.github.sommeri.less4j.core.ast.AbstractVariableDeclaration;
import com.github.sommeri.less4j.core.ast.Expression;

public class VariablesScope implements Cloneable {

  private Map<String, Expression> variables = new HashMap<String, Expression>();

  public VariablesScope(VariablesScope scope) {
    variables = new HashMap<String, Expression>(scope.variables);
  }

  public VariablesScope() {
  }

  public VariablesScope(VariablesScope scope1, VariablesScope scope2) {
    this(scope1);
    variables.putAll(scope2.variables);
  }

  public Expression getValue(String name) {
    return variables.get(name);
  }

  public void addDeclaration(AbstractVariableDeclaration node) {
    variables.put(node.getVariable().getName(), node.getValue());
  }

  public void addDeclaration(AbstractVariableDeclaration node, Expression replacementValue) {
    variables.put(node.getVariable().getName(), replacementValue);
  }

  public void addDeclaration(String name, Expression replacementValue) {
    variables.put(name, replacementValue);
  }

  public void addDeclarationIfNotPresent(String name, Expression replacementValue) {
    if (!variables.containsKey(name))
      variables.put(name, replacementValue);
  }

  public void removeDeclaration(String name) {
    variables.remove(name);
  }

  @Override
  //FIXME: change to protected
  public VariablesScope clone() {
    try {
      VariablesScope result = (VariablesScope) super.clone();
      result.variables = new HashMap<String, Expression>(variables);
      return result;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException("This should never happen.", e);
    }
  }

  public int size() {
    return variables.size();
  }

}