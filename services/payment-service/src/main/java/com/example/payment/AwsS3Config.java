package com.example.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/**
 * S3 client (AWS SDK v2).
 * - LocalStack: endpoint = http://localstack:4566 + forcePathStyle(true).
 * - Real AWS: empty endpoint => the SDK derives the address from the region itself,
 *   and takes credentials from the Task Role (not from access-key/secret-key).
 */
@Configuration
class AwsS3Config {

    @Bean
    S3Client s3Client(
            @Value("${app.s3.region}") String region,
            @Value("${app.s3.endpoint:}") String endpoint,
            @Value("${app.s3.access-key:test}") String accessKey,
            @Value("${app.s3.secret-key:test}") String secretKey) {

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true); // LocalStack requires path-style addressing

        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
