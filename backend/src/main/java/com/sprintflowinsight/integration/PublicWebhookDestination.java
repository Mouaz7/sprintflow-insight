package com.sprintflowinsight.integration;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.apache.hc.client5.http.DnsResolver;

/** Applies the same public-address policy at destination validation and at socket resolution. */
final class PublicWebhookDestination implements DnsResolver {
  @FunctionalInterface
  interface Lookup {
    InetAddress[] addresses(String host) throws UnknownHostException;
  }

  private final Lookup lookup;

  PublicWebhookDestination(Lookup lookup) {
    this.lookup = lookup;
  }

  static URI https(String value) {
    URI uri = URI.create(value);
    String host = uri.getHost();
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || host == null
        || host.isBlank()
        || uri.getRawUserInfo() != null
        || uri.getRawFragment() != null
        || uri.getPort() == 0
        || uri.getPort() > 65535) {
      throw new IllegalArgumentException(
          "A public HTTPS URL without credentials or a fragment is required.");
    }
    return uri;
  }

  @Override
  public InetAddress[] resolve(String host) throws UnknownHostException {
    InetAddress[] addresses = lookup.addresses(host);
    if (addresses.length == 0) throw new UnknownHostException("No webhook address resolved.");
    if (Arrays.stream(addresses).anyMatch(address -> !isPublic(address))) {
      throw new NonPublicAddress();
    }
    return addresses;
  }

  @Override
  public String resolveCanonicalHostname(String host) {
    return host;
  }

  static final class NonPublicAddress extends UnknownHostException {
    private NonPublicAddress() {
      super("Webhook addresses must be publicly routable.");
    }
  }

  static boolean isPublic(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) return false;
    byte[] bytes = address.getAddress();
    int first = Byte.toUnsignedInt(bytes[0]);
    int second = Byte.toUnsignedInt(bytes[1]);
    int third = Byte.toUnsignedInt(bytes[2]);
    if (bytes.length == 4) {
      return first != 0
          && first < 224
          && !(first == 100 && second >= 64 && second <= 127)
          && !(first == 192 && (second == 0 || (second == 88 && third == 99)))
          && !(first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100)))
          && !(first == 203 && second == 0 && third == 113);
    }
    int fourth = Byte.toUnsignedInt(bytes[3]);
    return (first & 0xe0) == 0x20
        && !(first == 0x20 && second == 0x02)
        && !(first == 0x20 && second == 0x01 && (third < 0x02 || (third == 0x0d && fourth == 0xb8)))
        && !(first == 0x3f && (second & 0xf0) == 0xf0);
  }
}
