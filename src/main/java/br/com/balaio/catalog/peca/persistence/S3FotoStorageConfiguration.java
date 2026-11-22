package br.com.balaio.catalog.peca.persistence;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@ConditionalOnProperty(name = "balaio.catalog.storage.enabled", havingValue = "true")
class S3FotoStorageConfiguration {
    @Bean(destroyMethod = "close")
    S3Client s3FotoClient(
            @Value("${balaio.catalog.storage.endpoint}") URI endpoint,
            @Value("${balaio.catalog.storage.region}") String region,
            @Value("${balaio.catalog.storage.access-key}") String accessKey,
            @Value("${balaio.catalog.storage.secret-key}") String secretKey) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("Credenciais próprias do catalog-service para S3 não configuradas.");
        }
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .httpClientBuilder(Apache5HttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(2))
                        .socketTimeout(Duration.ofSeconds(3))
                        .connectionMaxIdleTime(Duration.ofSeconds(5))
                        .expectContinueEnabled(false))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(Duration.ofSeconds(4))
                        .apiCallTimeout(Duration.ofSeconds(8)).build())
                // Files are already buffered and limited to 5 MiB. Send a known
                // content length without aws-chunked trailers or 100-continue.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .expectContinueEnabled(false).build())
                .build();
    }

    @Bean
    S3FotoStorageAdapter s3FotoStorageAdapter(S3Client client,
            @Value("${balaio.catalog.storage.bucket}") String bucket) {
        return new S3FotoStorageAdapter(client, bucket);
    }
}
