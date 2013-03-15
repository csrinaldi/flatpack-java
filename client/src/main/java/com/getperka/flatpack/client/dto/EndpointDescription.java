/*
 * #%L
 * FlatPack Client
 * %%
 * Copyright (C) 2012 Perka Inc.
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
package com.getperka.flatpack.client.dto;

import static com.getperka.flatpack.util.FlatPackTypes.UTF8;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.security.PermitAll;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.TraversalMode;
import com.getperka.flatpack.ext.Type;

/**
 * Describes an {@code HTTP} request endpoint.
 */
@PermitAll
public class EndpointDescription extends BaseHasUuid {
  private String docString;
  private Type entity;
  private List<TypeDescription> extraReturnData;
  private String method;
  private String path;
  private List<ParameterDescription> pathParameters;
  private List<ParameterDescription> queryParameters;
  private String returnDocString;
  private Type returnType;
  private Set<String> roleNames;
  private TraversalMode traversalMode;

  public EndpointDescription(String method, String path) {
    this.method = method;
    this.path = path;
  }

  /**
   * Used for deserialization.
   */
  EndpointDescription() {}

  /**
   * A documentation string describing the endpoint.
   */
  public String getDocString() {
    return docString;
  }

  /**
   * The expected entity type for the request. Generally, the {@code HTTP POST} body.
   */
  public Type getEntity() {
    return entity;
  }

  /**
   * Describes entities that may be added to a "bag-style" payload in addition to entities directly
   * reachable from the payload's {@code value}.
   */
  public List<TypeDescription> getExtraReturnData() {
    return extraReturnData;
  }

  /**
   * The HTTP method used to access the endpoint.
   */
  public String getMethod() {
    return method;
  }

  /**
   * The path used to access the endpoint.
   */
  public String getPath() {
    return path;
  }

  /**
   * Describes any parameters embedded in {@link #getPath()}.
   */
  public List<ParameterDescription> getPathParameters() {
    return pathParameters;
  }

  /**
   * Describes any query parameters for the endpoint.
   */
  public List<ParameterDescription> getQueryParameters() {
    return queryParameters;
  }

  /**
   * Provides additional information about the return value. This is analogous to a JavaDoc
   * {@literal @return}.
   */
  public String getReturnDocString() {
    return returnDocString;
  }

  /**
   * The expected contents for the HTTP response.
   */
  public Type getReturnType() {
    return returnType;
  }

  /**
   * Return the role names that are allowed to access the endpoint. A {@code null} value means that
   * all roles are allowed, while a zero-length value means that no roles are allowed.
   */
  public Set<String> getRoleNames() {
    return roleNames;
  }

  /**
   * The default traversal mode for data returned from the endpoint.
   */
  public TraversalMode getTraversalMode() {
    return traversalMode;
  }

  public void setDocString(String docString) {
    this.docString = docString;
  }

  public void setEntity(Type entity) {
    this.entity = entity;
  }

  public void setExtraReturnData(List<TypeDescription> extraReturnData) {
    this.extraReturnData = extraReturnData;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public void setPathParameters(List<ParameterDescription> pathParameters) {
    this.pathParameters = pathParameters;
  }

  public void setQueryParameters(List<ParameterDescription> parameters) {
    this.queryParameters = parameters;
  }

  public void setReturnDocString(String returnDocumentation) {
    this.returnDocString = returnDocumentation;
  }

  public void setReturnType(Type returnType) {
    this.returnType = returnType;
  }

  public void setRoleNames(Set<String> roleNames) {
    this.roleNames = roleNames;
  }

  public void setTraversalMode(TraversalMode traversalMode) {
    this.traversalMode = traversalMode;
  }

  @Override
  public String toString() {
    return method + " " + path;
  }

  @Override
  protected UUID defaultUuid() {
    if (method == null || path == null) {
      throw new IllegalStateException();
    }
    try {
      return UUID.nameUUIDFromBytes((method + ":" + path).getBytes(UTF8));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
}
