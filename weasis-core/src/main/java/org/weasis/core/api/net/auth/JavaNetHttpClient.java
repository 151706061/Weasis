/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import com.github.scribejava.core.httpclient.multipart.BodyPartPayload;
import com.github.scribejava.core.httpclient.multipart.ByteArrayBodyPartPayload;
import com.github.scribejava.core.httpclient.multipart.MultipartPayload;
import com.github.scribejava.core.model.OAuthAsyncRequestCallback;
import com.github.scribejava.core.model.OAuthConstants;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.weasis.core.api.net.HttpUtils;
import org.weasis.core.api.net.NetworkUtil;

public class JavaNetHttpClient implements com.github.scribejava.core.httpclient.HttpClient {

  private final JavaNetHttpClientConfig config;

  public JavaNetHttpClient() {
    this(new JavaNetHttpClientConfig());
  }

  public JavaNetHttpClient(JavaNetHttpClientConfig config) {
    this.config = config;
  }

  @Override
  public void close() throws IOException {
    // No need to close anything, since java.net.http.HttpClient is managed externally.
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
        userAgent,
        headers,
        httpVerb,
        completeUrl,
        JavaNetHttpClient.BodyType.BYTE_ARRAY,
        bodyContents,
        callback,
        converter);
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
        userAgent,
        headers,
        httpVerb,
        completeUrl,
        JavaNetHttpClient.BodyType.MULTIPART,
        bodyContents,
        callback,
        converter);
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
        userAgent,
        headers,
        httpVerb,
        completeUrl,
        JavaNetHttpClient.BodyType.STRING,
        bodyContents,
        callback,
        converter);
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
        userAgent,
        headers,
        httpVerb,
        completeUrl,
        BodyType.STREAM,
        bodyContents,
        callback,
        converter);
  }

  private <T> Future<T> doExecuteAsync(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      JavaNetHttpClient.BodyType bodyType,
      Object bodyContents,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {

    HttpClient client =
        HttpUtils.getHttpClient(
            Duration.ofMillis(config.getConnectTimeout()),
            HttpClient.Redirect.NORMAL,
            config.getProxy());
    Builder requestBuilder =
        getRequestBuilder(userAgent, headers, httpVerb, completeUrl, bodyType, bodyContents);

    return client
        .sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
        .thenApply(
            r -> {
              try {
                @SuppressWarnings("unchecked")
                final T t =
                    converter == null
                        ? (T) r
                        : converter.convert(
                            new Response(
                                r.statusCode(), r.version().toString(), parseHeaders(r), r.body()));
                if (callback != null) {
                  callback.onCompleted(t);
                }

                return t;
              } catch (IOException e) {
                if (callback != null) {
                  callback.onThrowable(e);
                }
                return null;
              }
            });
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      byte[] bodyContents)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(
        userAgent,
        headers,
        httpVerb,
        completeUrl,
        JavaNetHttpClient.BodyType.BYTE_ARRAY,
        bodyContents);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      MultipartPayload multipartPayloads)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(
        userAgent,
        headers,
        httpVerb,
        completeUrl,
        JavaNetHttpClient.BodyType.MULTIPART,
        multipartPayloads);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      String bodyContents)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(
        userAgent, headers, httpVerb, completeUrl, JavaNetHttpClient.BodyType.STRING, bodyContents);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      File bodyContents)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(userAgent, headers, httpVerb, completeUrl, BodyType.STREAM, bodyContents);
  }

  private Response doExecute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      JavaNetHttpClient.BodyType bodyType,
      Object bodyContents)
      throws IOException {

    HttpClient client =
        HttpUtils.getHttpClient(Duration.ofMillis(NetworkUtil.getUrlConnectionTimeout()));
    Builder requestBuilder =
        getRequestBuilder(userAgent, headers, httpVerb, completeUrl, bodyType, bodyContents);

    HttpResponse.BodyHandler<InputStream> bodyHandler = HttpResponse.BodyHandlers.ofInputStream();
    try {
      HttpResponse<InputStream> response = client.send(requestBuilder.build(), bodyHandler);
      final int responseCode = response.statusCode();
      return new Response(
          responseCode, response.version().toString(), parseHeaders(response), response.body());
    } catch (InterruptedException e) {
      throw new IOException("Request execution was interrupted", e);
    }
  }

  private static Builder getRequestBuilder(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      BodyType bodyType,
      Object bodyContents) {
    Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(URI.create(completeUrl))
            .timeout(Duration.ofMillis(NetworkUtil.getUrlReadTimeout()));
    addHeaders(requestBuilder, headers, userAgent);

    if (httpVerb.isPermitBody()) {
      bodyType.setBody(requestBuilder, bodyContents, httpVerb);
    }
    return requestBuilder;
  }

  private enum BodyType {
    BYTE_ARRAY {
      @Override
      void setBody(HttpRequest.Builder requestBuilder, Object bodyContents, Verb httpVerb) {
        addBody(requestBuilder, (byte[]) bodyContents, httpVerb);
      }
    },
    MULTIPART {
      @Override
      void setBody(HttpRequest.Builder requestBuilder, Object bodyContents, Verb httpVerb) {
        addBody(requestBuilder, (MultipartPayload) bodyContents, httpVerb);
      }
    },
    STREAM {
      @Override
      void setBody(HttpRequest.Builder requestBuilder, Object bodyContents, Verb httpVerb) {
        addBody(requestBuilder, (File) bodyContents, httpVerb);
      }
    },
    STRING {
      @Override
      void setBody(HttpRequest.Builder requestBuilder, Object bodyContents, Verb httpVerb) {
        addBody(requestBuilder, ((String) bodyContents).getBytes(StandardCharsets.UTF_8), httpVerb);
      }
    };

    abstract void setBody(HttpRequest.Builder requestBuilder, Object bodyContents, Verb httpVerb);
  }

  public static Map<String, String> parseHeaders(HttpResponse<?> response) {
    final Map<String, String> headers = new HashMap<>();

    for (Map.Entry<String, List<String>> headerField : response.headers().map().entrySet()) {
      final String key = headerField.getKey();
      final String value = headerField.getValue().getFirst();
      if ("Content-Encoding".equalsIgnoreCase(key)) { // NON-NLS
        headers.put("Content-Encoding", value); // NON-NLS
      } else {
        headers.put(key, value);
      }
    }
    return headers;
  }

  public static void addHeaders(
      HttpRequest.Builder requestBuilder, Map<String, String> headers, String userAgent) {
    for (Map.Entry<String, String> header : headers.entrySet()) {
      requestBuilder.header(header.getKey(), header.getValue());
    }

    if (userAgent != null) {
      requestBuilder.header(OAuthConstants.USER_AGENT_HEADER_NAME, userAgent);
    }
  }

  private static void addBody(HttpRequest.Builder requestBuilder, byte[] content, Verb httpVerb) {
    final int contentLength = content.length;
    if (httpVerb.isRequiresBody() || contentLength > 0) {
      if (contentLength > 0) {
        requestBuilder.method(httpVerb.name(), HttpRequest.BodyPublishers.ofByteArray(content));
      } else {
        requestBuilder.method(httpVerb.name(), HttpRequest.BodyPublishers.noBody());
      }
    }
  }

  private static void addBody(HttpRequest.Builder requestBuilder, File content, Verb httpVerb) {
    final long contentLength = content.length();
    if (httpVerb.isRequiresBody() || contentLength > 0) {
      if (contentLength > 0) {
        try {
          requestBuilder.method(
              httpVerb.name(), HttpRequest.BodyPublishers.ofFile(content.toPath()));
        } catch (FileNotFoundException e) {
          throw new IllegalStateException("File not found", e);
        }
      } else {
        requestBuilder.method(httpVerb.name(), HttpRequest.BodyPublishers.noBody());
      }
    }
  }

  public static void addBody(
      HttpRequest.Builder requestBuilder, MultipartPayload multipartPayload, Verb httpVerb) {

    for (Map.Entry<String, String> header : multipartPayload.getHeaders().entrySet()) {
      requestBuilder.header(header.getKey(), header.getValue());
    }

    if (httpVerb.isRequiresBody()) {
      setMultipartPayload(requestBuilder, multipartPayload, httpVerb);
    }
  }

  public static void setMultipartPayload(
      HttpRequest.Builder requestBuilder, MultipartPayload multipartPayload, Verb httpVerb) {
    StringBuilder buf = new StringBuilder();

    final String preamble = multipartPayload.getPreamble();
    if (preamble != null) {
      buf.append(preamble);
      buf.append("\r\n");
    }
    final List<BodyPartPayload> bodyParts = multipartPayload.getBodyParts();
    if (!bodyParts.isEmpty()) {
      final String boundary = multipartPayload.getBoundary();

      for (BodyPartPayload bodyPart : bodyParts) {
        buf.append("--");
        buf.append(boundary);
        buf.append("\r\n");

        final Map<String, String> bodyPartHeaders = bodyPart.getHeaders();
        if (bodyPartHeaders != null) {
          for (Map.Entry<String, String> header : bodyPartHeaders.entrySet()) {
            buf.append(header.getKey());
            buf.append(": ");
            buf.append(header.getValue());
            buf.append("\r\n");
          }
        }

        buf.append("\r\n");
        requestBuilder.method(httpVerb.name(), HttpRequest.BodyPublishers.ofString(buf.toString()));
        buf.setLength(0);

        switch (bodyPart) {
          case MultipartPayload multi -> setMultipartPayload(requestBuilder, multi, httpVerb);
          case ByteArrayBodyPartPayload byteArrayBodyPart ->
              requestBuilder.method(
                  httpVerb.name(),
                  HttpRequest.BodyPublishers.ofByteArray(
                      byteArrayBodyPart.getPayload(),
                      byteArrayBodyPart.getOff(),
                      byteArrayBodyPart.getLen()));
          case FileBodyPartPayload fileBodyPart -> {
            requestBuilder.method(
                httpVerb.name(),
                HttpRequest.BodyPublishers.ofInputStream(
                    () -> {
                      try {
                        return fileBodyPart.getPayload().get();
                      } catch (IOException e) {
                        throw new IllegalStateException("Cannot get input stream from file", e);
                      }
                    }));
          }
          default -> throw new AssertionError(bodyPart.getClass());
        }
        buf.append("\r\n"); // CRLF for the next (starting or closing) boundary
      }

      buf.append("--");
      buf.append(boundary);
      buf.append("--");

      final String epilogue = multipartPayload.getEpilogue();
      if (epilogue != null) {
        buf.append("\r\n");
        buf.append(epilogue);
      }
    }
    requestBuilder.method(httpVerb.name(), HttpRequest.BodyPublishers.ofString(buf.toString()));
    buf.setLength(0);
  }
}
