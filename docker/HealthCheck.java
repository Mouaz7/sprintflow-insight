import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Container health probe without curl or wget in the runtime image.
 * Exits 0 only when the actuator health endpoint answers 200 within the timeout.
 */
public final class HealthCheck {
  private HealthCheck() {}

  public static void main(String[] args) {
    String port = System.getenv().getOrDefault("PORT", "8081");
    try {
      HttpURLConnection connection =
          (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/actuator/health").toURL().openConnection();
      connection.setConnectTimeout(2_000);
      connection.setReadTimeout(3_000);
      int status = connection.getResponseCode();
      connection.disconnect();
      System.exit(status == 200 ? 0 : 1);
    } catch (Exception failure) {
      System.err.println("Health check failed: " + failure);
      System.exit(1);
    }
  }
}
