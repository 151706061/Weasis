/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import com.github.scribejava.core.exceptions.OAuthException;
import com.github.scribejava.core.httpclient.HttpClient;
import com.github.scribejava.core.httpclient.jdk.JDKHttpFuture;
import com.github.scribejava.core.httpclient.multipart.BodyPartPayload;
import com.github.scribejava.core.httpclient.multipart.ByteArrayBodyPartPayload;
import com.github.scribejava.core.httpclient.multipart.MultipartPayload;
import com.github.scribejava.core.model.OAuthAsyncRequestCallback;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.weasis.core.api.net.NetworkUtil;
import org.weasis.core.util.StringUtil;

/** HTTP client implementation using HttpURLConnection for OAuth requests. */
public class BasicHttpClient implements HttpClient {

  @Override
  public void close() {
    // No resources to clean up
  }

  @Override
  public <T> Future<T> executeAsync(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      byte[] bodyContents,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {

    return doExecuteAsync(
        userAgent, headers, httpVerb, completeUrl, bodyContents, callback, converter);
  }

  @Override
  public <T> Future<T> executeAsync(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      MultipartPayload bodyContents,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {

    return doExecuteAsync(
        userAgent, headers, httpVerb, completeUrl, bodyContents, callback, converter);
  }

  @Override
  public <T> Future<T> executeAsync(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      String bodyContents,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {

    return doExecuteAsync(
        userAgent, headers, httpVerb, completeUrl, bodyContents, callback, converter);
  }

  @Override
  public <T> Future<T> executeAsync(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      File bodyContents,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {
    return doExecuteAsync(
        userAgent, headers, httpVerb, completeUrl, bodyContents, callback, converter);
  }

  private <T> Future<T> doExecuteAsync(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      Object bodyContents,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {
    try {
      var response = doExecute(userAgent, headers, httpVerb, completeUrl, bodyContents);
      @SuppressWarnings("unchecked")
      T result = converter == null ? (T) response : converter.convert(response);
      if (callback != null) {
        callback.onCompleted(result);
      }
      return new JDKHttpFuture<>(result);
    } catch (IOException | RuntimeException e) {
      if (callback != null) {
        callback.onThrowable(e);
      }
      return new JDKHttpFuture<>(e);
    }
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      byte[] bodyContents)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(userAgent, headers, httpVerb, completeUrl, bodyContents);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      MultipartPayload multipartPayloads)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(userAgent, headers, httpVerb, completeUrl, multipartPayloads);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      String bodyContents)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(userAgent, headers, httpVerb, completeUrl, bodyContents);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      File bodyContents)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(userAgent, headers, httpVerb, completeUrl, bodyContents);
  }

  private Response doExecute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      Object bodyContents)
      throws IOException {

    var connection = createConnection(completeUrl, httpVerb);
    addHeaders(connection, headers, userAgent);

    if (httpVerb.isPermitBody()) {
      setBody(connection, bodyContents, httpVerb.isRequiresBody());
    }

    return executeRequest(connection);
  }

  private static HttpURLConnection createConnection(String completeUrl, Verb httpVerb)
      throws IOException {
    var url = URI.create(completeUrl).toURL();
    var connection = (HttpURLConnection) url.openConnection();

    connection.setInstanceFollowRedirects(true);
    connection.setRequestMethod(httpVerb.name());
    connection.setConnectTimeout(NetworkUtil.getUrlConnectionTimeout());
    connection.setReadTimeout(NetworkUtil.getUrlReadTimeout());
    return connection;
  }

  private static Response executeRequest(HttpURLConnection connection) throws IOException {
    try {
      connection.connect();
      int responseCode = connection.getResponseCode();
      return new Response(
          responseCode,
          connection.getResponseMessage(),
          parseHeaders(connection),
          isSuccessfulResponse(responseCode)
              ? connection.getInputStream()
              : connection.getErrorStream());
    } catch (UnknownHostException e) {
      throw new OAuthException("The IP address of a host could not be determined.", e);
    }
  }

  private static boolean isSuccessfulResponse(int responseCode) {
    return responseCode >= 200 && responseCode < 400;
  }

  private static void setBody(
      HttpURLConnection connection, Object bodyContents, boolean requiresBody) throws IOException {
    switch (bodyContents) {
      case null -> {
        if (requiresBody) {
          throw new IOException("Body content is required but null");
        }
      }
      case byte[] bytes -> addBody(connection, bytes, requiresBody);
      case String str -> addBody(connection, str.getBytes(StandardCharsets.UTF_8), requiresBody);
      case File file -> addBody(connection, file.toPath(), requiresBody);
      case Path path -> addBody(connection, path, requiresBody);
      case MultipartPayload multi -> addBody(connection, multi, requiresBody);
      default ->
          throw new IllegalArgumentException("Unsupported body type: " + bodyContents.getClass());
    }
  }

  private static Map<String, String> parseHeaders(HttpURLConnection conn) {
    Map<String, String> headers = new HashMap<>();
    var headerFields = conn.getHeaderFields();

    for (var entry : headerFields.entrySet()) {
      String key = entry.getKey();
      var values = entry.getValue();
      if (StringUtil.hasText(key) && !values.isEmpty()) {
        headers.put(key, values.getFirst());
      }
    }
    return headers;
  }

  private static void addHeaders(
      HttpURLConnection connection, Map<String, String> headers, String userAgent) {
    if (StringUtil.hasText(userAgent)) {
      connection.addRequestProperty("User-Agent", userAgent);
    }

    if (headers != null) {
      headers.forEach(connection::addRequestProperty);
    }
  }

  private static void addBody(HttpURLConnection connection, Path filePath, boolean requiresBody)
      throws IOException {
    if (filePath == null || !Files.exists(filePath)) {
      if (requiresBody) {
        throw new IOException("File does not exist: " + filePath);
      }
      return;
    }

    long contentLength = Files.size(filePath);
    try (var outputStream = prepareConnectionForBodyAndGetOutputStream(connection, contentLength);
        var inputStream = Files.newInputStream(filePath)) {
      inputStream.transferTo(outputStream);
    }
  }

  private static void addBody(HttpURLConnection connection, byte[] content, boolean requiresBody)
      throws IOException {
    if (content == null || content.length == 0) {
      if (requiresBody) {
        throw new IOException("Body content is required but empty");
      }
      return;
    }

    try (var outputStream =
        prepareConnectionForBodyAndGetOutputStream(connection, content.length)) {
      outputStream.write(content);
    }
  }

  public static void addBody(
      HttpURLConnection connection, MultipartPayload multipartPayload, boolean requiresBody)
      throws IOException {
    if (multipartPayload == null) {

      if (requiresBody) {
        throw new IOException("Multipart payload is required but null");
      }
      return;
    }

    var bodySuppliers = new ArrayList<BodySupplier<InputStream>>();
    prepareMultipartPayload(bodySuppliers, multipartPayload);
    long contentLength =
        bodySuppliers.stream().mapToLong(BodySupplier::length).reduce(0L, Long::sum);
    try (var outputStream = prepareConnectionForBodyAndGetOutputStream(connection, contentLength)) {
      for (var supplier : bodySuppliers) {
        try (var inputStream = supplier.get()) {
          inputStream.transferTo(outputStream);
        }
      }
    }
  }

  private static OutputStream prepareConnectionForBodyAndGetOutputStream(
      HttpURLConnection connection, long contentLength) throws IOException {
    connection.setDoOutput(true);
    connection.setUseCaches(false);

    if (contentLength >= 0) {
      if (contentLength <= Integer.MAX_VALUE) {
        connection.setFixedLengthStreamingMode((int) contentLength);
      } else {
        connection.setFixedLengthStreamingMode(contentLength);
      }
    } else {
      connection.setChunkedStreamingMode(0);
    }
    return connection.getOutputStream();
  }

  private static void prepareMultipartPayload(
      List<BodySupplier<InputStream>> bodySuppliers, MultipartPayload multipartPayload) {

    addPreambleIfPresent(bodySuppliers, multipartPayload.getPreamble());

    var bodyParts = multipartPayload.getBodyParts();
    if (!bodyParts.isEmpty()) {
      processBodyParts(bodySuppliers, bodyParts, multipartPayload.getBoundary());
      addEpilogueIfPresent(bodySuppliers, multipartPayload.getEpilogue());
    } else {
      bodySuppliers.add(newBodySupplier(new StringBuilder()));
    }
  }

  private static void addPreambleIfPresent(
      List<BodySupplier<InputStream>> bodySuppliers, String preamble) {
    if (preamble != null) {
      var buf = new StringBuilder(preamble).append("\r\n");
      bodySuppliers.add(newBodySupplier(buf));
    }
  }

  private static void processBodyParts(
      List<BodySupplier<InputStream>> bodySuppliers,
      List<BodyPartPayload> bodyParts,
      String boundary) {

    for (var bodyPart : bodyParts) {
      addBoundaryAndHeaders(bodySuppliers, bodyPart, boundary);
      addBodyPartContent(bodySuppliers, bodyPart);
    }

    addClosingBoundary(bodySuppliers, boundary);
  }

  private static void addBoundaryAndHeaders(
      List<BodySupplier<InputStream>> bodySuppliers, BodyPartPayload bodyPart, String boundary) {

    var buf = new StringBuilder().append("--").append(boundary).append("\r\n");

    var headers = bodyPart.getHeaders();
    if (headers != null) {
      headers.forEach((key, value) -> buf.append(key).append(": ").append(value).append("\r\n"));
    }

    buf.append("\r\n");
    bodySuppliers.add(newBodySupplier(buf));
  }

  private static void addBodyPartContent(
      List<BodySupplier<InputStream>> bodySuppliers, BodyPartPayload bodyPart) {

    switch (bodyPart) {
      case MultipartPayload multi -> prepareMultipartPayload(bodySuppliers, multi);
      case ByteArrayBodyPartPayload byteArrayPart ->
          bodySuppliers.add(
              newBodySupplier(
                  byteArrayPart.getPayload(), byteArrayPart.getOff(), byteArrayPart.getLen()));
      case FileBodyPartPayload filePart -> bodySuppliers.add(filePart.getPayload());
      default ->
          throw new IllegalArgumentException("Unsupported body part type: " + bodyPart.getClass());
    }

    bodySuppliers.add(newBodySupplier(new StringBuilder("\r\n")));
  }

  private static void addClosingBoundary(
      List<BodySupplier<InputStream>> bodySuppliers, String boundary) {
    var closingBoundary = new StringBuilder().append("--").append(boundary).append("--");
    bodySuppliers.add(newBodySupplier(closingBoundary));
  }

  private static void addEpilogueIfPresent(
      List<BodySupplier<InputStream>> bodySuppliers, String epilogue) {
    if (epilogue != null) {
      var buf = new StringBuilder("\r\n").append(epilogue);
      bodySuppliers.add(newBodySupplier(buf));
    }
  }

  private static BodySupplier<InputStream> newBodySupplier(StringBuilder buf) {
    return BodySupplier.ofString(buf.toString());
  }

  private static BodySupplier<InputStream> newBodySupplier(byte[] payload, int off, int len) {
    return BodySupplier.ofBytes(payload, off, len);
  }
}
