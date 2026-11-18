// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cliente HTTP mínimo para os IT: peticións reais contra a API arrincada no porto aleatorio. */
public final class Api {

  public static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private final int port;

  public Api(int port) {
    this.port = port;
  }

  public Call get(String path) {
    return new Call("GET", path, null);
  }

  public Call delete(String path) {
    return new Call("DELETE", path, null);
  }

  public Call post(String path, Object body) {
    return new Call("POST", path, body);
  }

  public Call put(String path, Object body) {
    return new Call("PUT", path, body);
  }

  public Call method(String method, String path, Object body) {
    return new Call(method, path, body);
  }

  /** Unha petición en construción. */
  public final class Call {
    private final String method;
    private final String path;
    private final Object body;
    private final Map<String, String> headers = new LinkedHashMap<>();

    private Call(String method, String path, Object body) {
      this.method = method;
      this.path = path;
      this.body = body;
    }

    public Call token(String token) {
      return header("Authorization", "Bearer " + token);
    }

    public Call ifMatch(long version) {
      return header("If-Match", "\"" + version + "\"");
    }

    public Call language(String language) {
      return header("Accept-Language", language);
    }

    public Call header(String name, String value) {
      headers.put(name, value);
      return this;
    }

    public Response send() {
      HttpRequest.BodyPublisher publisher =
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(
                  body instanceof String s ? s : JSON.writeValueAsString(body));
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .method(method, publisher);
      if (body != null) {
        builder.header("Content-Type", "application/json");
      }
      headers.forEach(builder::header);
      try {
        HttpResponse<String> response =
            CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
  }

  /** Resposta con atallos para o JSON. */
  public record Response(HttpResponse<String> raw) {
    public int status() {
      return raw.statusCode();
    }

    public String body() {
      return raw.body();
    }

    public JsonNode json() {
      return JSON.readTree(raw.body());
    }

    public String header(String name) {
      return raw.headers().firstValue(name).orElse(null);
    }

    public String contentType() {
      return header("Content-Type");
    }

    public long etagVersion() {
      String etag = header("ETag");
      return Long.parseLong(etag.replace("W/", "").replace("\"", ""));
    }

    @Override
    public String toString() {
      return status() + " " + body();
    }
  }
}
