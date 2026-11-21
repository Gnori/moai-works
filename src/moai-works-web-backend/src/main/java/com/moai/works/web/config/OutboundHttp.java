package com.moai.works.web.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * 다른 서비스(api · Keycloak)를 부르는 HTTP 클라이언트 — 모든 서비스가 HTTPS 라서, 누구의 인증서를 믿는지 여기서 정한다.
 *
 * <p>믿는 것 = JVM 이 원래 믿는 인증 기관 + {@code moai-works-web.tls.ca-file} 의 인증 기관(로컬은 mkcert).
 * 인증서 확인을 끄는 설정은 두지 않는다 — 믿을 인증 기관을 더하기만 한다.
 */
@Configuration
public class OutboundHttp {

  private static final Logger log = LoggerFactory.getLogger(OutboundHttp.class);

  @Bean
  public ClientHttpRequestFactory outboundRequestFactory(WebProperties properties) {
    String caFile = properties.tls() == null ? null : properties.tls().caFile();
    if (caFile == null || caFile.isBlank()) {
      return new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
    }
    Path path = Path.of(caFile.strip()).toAbsolutePath().normalize();
    try {
      SSLContext context = trusting(path);
      log.info("다른 서비스를 부를 때 믿는 인증 기관에 {} 을(를) 더했다", path);
      return new JdkClientHttpRequestFactory(HttpClient.newBuilder().sslContext(context).build());
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException("인증 기관 파일을 읽지 못했다: " + path
          + " — 로컬이면 infra/tls/make-certs.sh 를 돌리고, 공인 인증서를 쓰면 MOAI_WORKS_TLS_CA 를 비운다", e);
    }
  }

  /** JVM 이 원래 믿는 인증 기관에 파일의 인증 기관을 더한 TLS 문맥 */
  static SSLContext trusting(Path caFile) throws IOException, GeneralSecurityException {
    KeyStore trusted = KeyStore.getInstance(KeyStore.getDefaultType());
    trusted.load(null, null);
    int n = 0;
    TrustManagerFactory defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    defaults.init((KeyStore) null);
    for (TrustManager manager : defaults.getTrustManagers()) {
      if (manager instanceof X509TrustManager x509) {
        for (X509Certificate issuer : x509.getAcceptedIssuers()) {
          trusted.setCertificateEntry("default-" + n++, issuer);
        }
      }
    }
    try (InputStream in = Files.newInputStream(caFile)) {
      for (Certificate extra : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
        trusted.setCertificateEntry("extra-" + n++, extra);
      }
    }
    TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init(trusted);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, factory.getTrustManagers(), null);
    return context;
  }
}
