package com.sprintflowinsight.auth;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/** Adapt the Google library's certificate GETs to the application's bounded RestClient. */
public final class GoogleRestClientTransport extends HttpTransport {
  private final RestClient client;

  public GoogleRestClientTransport(RestClient client) {
    this.client = client;
  }

  @Override
  protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
    if (!"GET".equals(method))
      throw new IOException("Only signing-key GET requests are supported.");
    return new LowLevelHttpRequest() {
      private final HttpHeaders headers = new HttpHeaders();

      @Override
      public void addHeader(String name, String value) {
        headers.add(name, value);
      }

      @Override
      public LowLevelHttpResponse execute() throws IOException {
        try {
          return client
              .get()
              .uri(url)
              .headers(target -> target.addAll(headers))
              .exchange(
                  (request, response) -> {
                    byte[] bytes = response.getBody().readNBytes(1_048_577);
                    if (bytes.length > 1_048_576)
                      throw new IOException("Signing-key response exceeds the limit.");
                    return new BufferedResponse(
                        response.getStatusCode().value(),
                        response.getStatusText(),
                        response.getHeaders(),
                        bytes);
                  });
        } catch (org.springframework.web.client.RestClientException failure) {
          throw new IOException("Signing-key request failed.", failure);
        }
      }
    };
  }

  private static final class BufferedResponse extends LowLevelHttpResponse {
    private final int status;
    private final String reason;
    private final HttpHeaders headers;
    private final byte[] body;
    private final List<String> names = new ArrayList<>();
    private final List<String> values = new ArrayList<>();

    private BufferedResponse(int status, String reason, HttpHeaders headers, byte[] body) {
      this.status = status;
      this.reason = reason;
      this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
      this.body = body;
      headers.forEach(
          (name, entries) ->
              entries.forEach(
                  value -> {
                    names.add(name);
                    values.add(value);
                  }));
    }

    @Override
    public InputStream getContent() {
      return new ByteArrayInputStream(body);
    }

    @Override
    public String getContentEncoding() {
      return headers.getFirst(HttpHeaders.CONTENT_ENCODING);
    }

    @Override
    public long getContentLength() {
      return body.length;
    }

    @Override
    public String getContentType() {
      return headers.getFirst(HttpHeaders.CONTENT_TYPE);
    }

    @Override
    public String getStatusLine() {
      return "HTTP " + status + " " + reason;
    }

    @Override
    public int getStatusCode() {
      return status;
    }

    @Override
    public String getReasonPhrase() {
      return reason;
    }

    @Override
    public int getHeaderCount() {
      return names.size();
    }

    @Override
    public String getHeaderName(int index) {
      return names.get(index);
    }

    @Override
    public String getHeaderValue(int index) {
      return values.get(index);
    }
  }
}
