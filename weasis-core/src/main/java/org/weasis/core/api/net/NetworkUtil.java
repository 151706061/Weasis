/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
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
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
import org.weasis.core.util.StreamIOException;
import org.weasis.core.util.StringUtil;

/** Network operations utility for HTTP connections, redirects, and URL handling. */
public final class NetworkUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(NetworkUtil.class);

  private static final int MAX_REDIRECTS = 3;
  private static final String HTTP_SCHEME_PREFIX = "http";

  private NetworkUtil() {}

  public static int getUrlConnectionTimeout() {
    return StringUtil.getInt(System.getProperty("UrlConnectionTimeout"), 5000);
  }

  public static int getUrlReadTimeout() {
    return StringUtil.getInt(System.getProperty("UrlReadTimeout"), 15000);
  }

  public static URI getURI(String pathOrUri) throws MalformedURLException, URISyntaxException {
    if (!pathOrUri.startsWith(HTTP_SCHEME_PREFIX)) {
      URI fileUri = tryCreateFileUri(pathOrUri);
      if (fileUri != null) {
        return fileUri;
      }
    }
    return URI.create(pathOrUri);
  }

  private static URI tryCreateFileUri(String pathOrUri) {
    try {
      Path path = Path.of(pathOrUri);
      return Files.isReadable(path) ? path.toUri() : null;
    } catch (Exception e) {
      LOGGER.debug("Failed to create file URI for path: {}", pathOrUri, e);
      return null;
    }
  }

  public static HttpStream getHttpResponse(
      String url, URLParameters urlParameters, AuthMethod authMethod) throws IOException {
    return getHttpResponse(url, urlParameters, authMethod, null);
  }

  public static HttpStream getHttpResponse(
      String url, URLParameters urlParameters, AuthMethod authMethod, OAuthRequest authRequest)
      throws IOException {
    if (isNoAuth(authMethod)) {
      return prepareConnection(URI.create(url).toURL().openConnection(), urlParameters);
    }
    OAuthRequest request =
        Objects.requireNonNullElseGet(
            authRequest,
            () -> new OAuthRequest(urlParameters.httpPost() ? Verb.POST : Verb.GET, url));
    return HttpUtils.executeAuthenticatedRequest(request, urlParameters, authMethod);
  }

  private static boolean isNoAuth(AuthMethod authMethod) {
    return authMethod == null || OAuth2ServiceFactory.NO_AUTH.equals(authMethod);
  }

  public static ClosableURLConnection getUrlConnection(String url, URLParameters urlParameters)
      throws IOException {
    return prepareConnection(URI.create(url).toURL().openConnection(), urlParameters);
  }

  public static ClosableURLConnection getUrlConnection(URL url, URLParameters urlParameters)
      throws IOException {
    return prepareConnection(url.openConnection(), urlParameters);
  }

  private static void setAppHeaders(URLConnection urlConnection) {
    urlConnection.setRequestProperty("User-Agent", AppProperties.WEASIS_USER_AGENT);
    urlConnection.setRequestProperty("Weasis-User", AppProperties.WEASIS_USER);
  }

  private static ClosableURLConnection prepareConnection(
      URLConnection urlConnection, URLParameters urlParameters) throws StreamIOException {
    urlParameters.headers().forEach(urlConnection::setRequestProperty);
    setAppHeaders(urlConnection);
    configureConnection(urlConnection, urlParameters);

    if (urlConnection instanceof HttpURLConnection httpConn) {
      return handleHttpConnection(httpConn, urlParameters);
    }

    return new ClosableURLConnection(urlConnection);
  }

  private static void configureConnection(URLConnection connection, URLParameters parameters) {
    connection.setConnectTimeout(parameters.connectTimeout());
    connection.setReadTimeout(parameters.readTimeout());
    connection.setAllowUserInteraction(parameters.allowUserInteraction());
    connection.setUseCaches(parameters.useCaches());
    connection.setIfModifiedSince(parameters.ifModifiedSince());
    connection.setDoInput(true);

    if (parameters.httpPost()) {
      connection.setDoOutput(true);
    }
  }

  private static ClosableURLConnection handleHttpConnection(
      HttpURLConnection httpConn, URLParameters parameters) throws StreamIOException {
    try {
      if (parameters.httpPost()) {
        httpConn.setRequestMethod("POST");
      } else {
        return new ClosableURLConnection(readResponse(httpConn, parameters.headers()));
      }
    } catch (StreamIOException e) {
      throw e;
    } catch (IOException e) {
      throw new StreamIOException(e);
    }
    return new ClosableURLConnection(httpConn);
  }

  public static URLConnection readResponse(
      HttpURLConnection httpConnection, Map<String, String> headers) throws IOException {
    int responseCode = httpConnection.getResponseCode();

    if (isSuccessResponse(responseCode)) {
      return httpConnection;
    }

    if (isRedirectResponse(responseCode)) {
      return applyRedirectionStream(httpConnection, headers);
    }

    handleErrorResponse(httpConnection, responseCode);
    throw new StreamIOException(httpConnection.getResponseMessage());
  }

  private static boolean isSuccessResponse(int code) {
    return code >= HttpURLConnection.HTTP_OK && code < HttpURLConnection.HTTP_MULT_CHOICE;
  }

  private static boolean isRedirectResponse(int code) {
    return code == HttpURLConnection.HTTP_MOVED_TEMP
        || code == HttpURLConnection.HTTP_MOVED_PERM
        || code == HttpURLConnection.HTTP_SEE_OTHER;
  }

  private static void handleErrorResponse(HttpURLConnection connection, int code)
      throws IOException {
    LOGGER.warn("HTTP Status {} - {}", code, connection.getResponseMessage());

    if (LOGGER.isTraceEnabled()) {
      logErrorResponse(connection);
    }
  }

  public static String read(URLConnection urlConnection) throws IOException {
    urlConnection.connect();
    try (var inputStream = urlConnection.getInputStream()) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  public static URLConnection applyRedirectionStream(
      URLConnection urlConnection, Map<String, String> headers) throws IOException {
    URLConnection connection = urlConnection;
    String redirectUrl = connection.getHeaderField("Location");

    for (int i = 0; i < MAX_REDIRECTS && redirectUrl != null; i++) {
      connection = followRedirect(connection, redirectUrl, headers);
      redirectUrl = connection.getHeaderField("Location");
    }

    return connection;
  }

  private static URLConnection followRedirect(
      URLConnection current, String redirectUrl, Map<String, String> headers) throws IOException {
    String cookies = current.getHeaderField("Set-Cookie");

    if (current instanceof HttpURLConnection httpConn) {
      httpConn.disconnect();
    }

    URLConnection newConnection = URI.create(redirectUrl).toURL().openConnection();

    if (cookies != null) {
      newConnection.setRequestProperty("Cookie", cookies);
    }
    if (headers != null && !headers.isEmpty()) {
      headers.forEach(newConnection::addRequestProperty);
    }
    return newConnection;
  }

  public static boolean isValidUrlLikeUri(String uri) {
    try {
      URI parsed = URI.create(uri);
      return parsed.getScheme() != null && parsed.getHost() != null;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static void logErrorResponse(HttpURLConnection connection) throws IOException {
    try (InputStream errorStream = connection.getErrorStream()) {
      if (errorStream != null) {
        String errorContent = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
        if (StringUtil.hasText(errorContent)) {
          LOGGER.trace("HttpURLConnection ERROR, server response: {}", errorContent);
        }
      }
    }
  }
}
