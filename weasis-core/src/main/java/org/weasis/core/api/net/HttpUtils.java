/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
import org.weasis.core.util.StreamIOException;

public class HttpUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpUtils.class);

  public static <S> HttpResponse<S> getHttpConnection(
      URL url, URLParameters urlParameters, HttpResponse.BodyHandler<S> bodyHandler)
      throws IOException, InterruptedException {
    return prepareConnection(url.toString(), urlParameters, bodyHandler);
  }

  public static HttpClient getHttpClient() {
    return getHttpClient(
        Duration.ofMillis(NetworkUtil.getUrlConnectionTimeout()),
        HttpClient.Redirect.NORMAL,
        ProxySelector.getDefault());
  }

  public static HttpClient getHttpClient(Duration duration) {
    return getHttpClient(duration, HttpClient.Redirect.NORMAL, ProxySelector.getDefault());
  }

  public static HttpClient getHttpClient(
      Duration duration, HttpClient.Redirect redirect, ProxySelector proxySelector) {
    return HttpClient.newBuilder()
        .connectTimeout(duration)
        .followRedirects(redirect)
        .proxy(proxySelector)
        .build();
  }

  public static HttpStream getHttpResponse(
      String url, URLParameters urlParameters, AuthMethod authMethod) throws IOException {
    return getHttpResponse(url, urlParameters, authMethod, null);
  }

  public static HttpStream getHttpResponse(
      String url, URLParameters urlParameters, AuthMethod authMethod, OAuthRequest authRequest)
      throws IOException {
    if (authMethod == null || OAuth2ServiceFactory.noAuth.equals(authMethod)) {
      HttpResponse<InputStream> response =
          prepareConnection(url, urlParameters, HttpResponse.BodyHandlers.ofInputStream());
      return new HttpResponseStream(response);
    }
    OAuthRequest request;
    request =
        Objects.requireNonNullElseGet(
            authRequest,
            () -> new OAuthRequest(urlParameters.isHttpPost() ? Verb.POST : Verb.GET, url));
    return prepareAuthConnection(request, urlParameters, authMethod);
  }

  private static <S> HttpResponse<S> prepareConnection(
      String url, URLParameters urlParameters, HttpResponse.BodyHandler<S> bodyHandler)
      throws IOException {
    HttpClient client = getHttpClient(Duration.ofMillis(urlParameters.getConnectTimeout()));
    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(urlParameters.getReadTimeout()));

    Map<String, String> headers = urlParameters.getUnmodifiableHeaders();
    if (!headers.isEmpty()) {
      for (Entry<String, String> element : headers.entrySet()) {
        requestBuilder.header(element.getKey(), element.getValue());
      }
    }
    updateHeadersWithAppProperties(requestBuilder);

    HttpRequest request =
        urlParameters.isHttpPost()
            ? requestBuilder.POST(HttpRequest.BodyPublishers.noBody()).build()
            : requestBuilder.GET().build();

    HttpResponse<S> response;
    try {
      response = client.send(request, bodyHandler);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    if (response.statusCode() != 200) {
      throw new IOException("Received error code : " + response.statusCode());
    }
    return response;
  }

  public static AuthResponse prepareAuthConnection(
      OAuthRequest request, URLParameters urlParameters, AuthMethod authMethod) throws IOException {
    Map<String, String> headers = urlParameters.getUnmodifiableHeaders();
    if (!headers.isEmpty()) {
      for (Entry<String, String> element : headers.entrySet()) {
        request.addHeader(element.getKey(), element.getValue());
      }
    }
    request.addHeader("User-Agent", AppProperties.WEASIS_USER_AGENT); // NON-NLS
    request.addHeader("Weasis-User", AppProperties.WEASIS_USER); // NON-NLS

    try {
      OAuth20Service service = OAuth2ServiceFactory.getService(authMethod);
      if (service == null) {
        throw new IllegalStateException("Not a valid authentication method: " + authMethod);
      }
      service.signRequest(authMethod.getToken(), request);
      return new AuthResponse(service.execute(request));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StreamIOException(e);
    } catch (Exception e) {
      throw new StreamIOException(e);
    }
  }

  private static void updateHeadersWithAppProperties(HttpRequest.Builder requestBuilder) {
    requestBuilder.header("User-Agent", AppProperties.WEASIS_USER_AGENT);
    requestBuilder.header("Weasis-User", AppProperties.WEASIS_USER);
  }
}
