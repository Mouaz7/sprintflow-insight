package com.sprintflowinsight.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class OutboundWebhookDestinationTest {
  @Test
  void requiresHttpsAndRejectsCredentialsAndFragments() {
    assertThat(PublicWebhookDestination.https("https://hooks.example.test/notify").getHost())
        .isEqualTo("hooks.example.test");
    for (String address :
        new String[] {
          "http://hooks.example.test/",
          "https://name@hooks.example.test/",
          "https://hooks.example.test/#fragment",
          "https:///relative"
        }) {
      assertThatThrownBy(() -> PublicWebhookDestination.https(address))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsAnyNonPublicResultFromTheDnsResolver() throws Exception {
    for (String address :
        new String[] {
          "127.0.0.1",
          "192.168.0.2",
          "100.64.1.2",
          "169.254.1.1",
          "0.1.2.3",
          "224.0.0.1",
          "::1",
          "fd01::a",
          "fe80::1",
          "2001:db8::1",
          "::ffff:192.168.0.2"
        }) {
      assertThat(PublicWebhookDestination.isPublic(InetAddress.getByName(address)))
          .as(address)
          .isFalse();
    }
    var publicAddress = InetAddress.getByName("1.1.1.1");
    var publicIpv6 = InetAddress.getByName("2606:4700:4700::1111");
    var localAddress = InetAddress.getByName("127.0.0.1");
    assertThat(
            new PublicWebhookDestination(host -> new InetAddress[] {publicAddress, publicIpv6})
                .resolve("hooks.example.test"))
        .containsExactly(publicAddress, publicIpv6);
    assertThatThrownBy(
            () ->
                new PublicWebhookDestination(
                        host -> new InetAddress[] {publicAddress, localAddress})
                    .resolve("hooks.example.test"))
        .isInstanceOf(PublicWebhookDestination.NonPublicAddress.class);
  }

  @Test
  void signsTheTimestampAndExactPayloadAndOnlyRetriesTransientResponses() {
    String payload = "{\"type\":\"task.created\"}";
    var signature = OutboundWebhookTransport.signature("sample-secret", "1700000000", payload);
    assertThat(signature).matches("sha256=[0-9a-f]{64}");
    assertThat(signature)
        .isNotEqualTo(OutboundWebhookTransport.signature("sample-secret", "1700000001", payload));
    assertThat(signature)
        .isNotEqualTo(
            OutboundWebhookTransport.signature("sample-secret", "1700000000", payload + " "));
    assertThat(OutboundWebhookTransport.Result.status(204).result()).isEqualTo("DELIVERED");
    assertThat(OutboundWebhookTransport.Result.status(503).retry()).isTrue();
    assertThat(OutboundWebhookTransport.Result.status(302).retry()).isFalse();
  }
}
