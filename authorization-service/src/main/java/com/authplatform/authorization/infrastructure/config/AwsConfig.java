package com.authplatform.authorization.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.net.URI;

@Configuration
public class AwsConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Bean
    @Profile("!local")
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Bean
    @Profile("!local")
    public SsmClient ssmClient() {
        return SsmClient.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Bean
    @Profile("local")
    public SecretsManagerClient localSecretsManagerClient(
            @Value("${aws.endpoint-override:http://localstack:4566}") String endpointUrl) {
        return SecretsManagerClient.builder()
            .region(Region.of(awsRegion))
            .endpointOverride(URI.create(endpointUrl))
            .credentialsProvider(
                software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")
                )
            )
            .build();
    }

    @Bean
    @Profile("local")
    public SsmClient localSsmClient(
            @Value("${aws.endpoint-override:http://localstack:4566}") String endpointUrl) {
        return SsmClient.builder()
            .region(Region.of(awsRegion))
            .endpointOverride(URI.create(endpointUrl))
            .credentialsProvider(
                software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")
                )
            )
            .build();
    }
}
