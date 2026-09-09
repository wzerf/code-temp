package com.wshake.infra.storage;

import com.wshake.service.port.StoragePort;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 按 {@code app.storage.type} 装配唯一 {@link StoragePort}。
 *
 * @author wshake
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {

    /**
     * 本地磁盘适配。
     *
     * @param properties 存储配置
     * @return local 端口
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "local", matchIfMissing = true)
    public StoragePort localStoragePort(StorageProperties properties) {
        properties.validate();
        return new LocalStorageAdapter(properties);
    }

    /**
     * S3 客户端。
     *
     * @param properties 存储配置
     * @return 可关闭的 S3Client
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "s3")
    public S3Client storageS3Client(StorageProperties properties) {
        properties.validate();
        StorageProperties.S3 s3 = properties.getS3();
        return S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .endpointOverride(URI.create(s3.getEndpoint()))
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())))
                .forcePathStyle(s3.isPathStyle())
                .build();
    }

    /**
     * S3 预签名客户端。
     *
     * @param properties 存储配置
     * @return 可关闭的 S3Presigner
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "s3")
    public S3Presigner storageS3Presigner(StorageProperties properties) {
        properties.validate();
        StorageProperties.S3 s3 = properties.getS3();
        return S3Presigner.builder()
                .endpointOverride(URI.create(s3.getEndpoint()))
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyle())
                        .build())
                .build();
    }

    /**
     * S3 适配。
     *
     * @param properties 存储配置
     * @param s3Client   客户端
     * @param s3Presigner 预签名客户端
     * @return s3 端口
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "s3")
    public StoragePort s3StoragePort(StorageProperties properties, S3Client s3Client, S3Presigner s3Presigner) {
        properties.validate();
        ensureBucketExists(s3Client, properties.getS3().getBucket());
        return new S3StorageAdapter(properties, s3Client, s3Presigner);
    }

    /**
     * 桶不存在则创建，避免新环境必须登录 MinIO 控制台手工建桶。
     *
     * @param s3Client S3 客户端
     * @param bucket   桶名
     */
    static void ensureBucketExists(S3Client s3Client, String bucket) {
        if (bucketExists(s3Client, bucket)) {
            return;
        }
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("已创建 S3 bucket: {}", bucket);
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ex) {
            log.debug("S3 bucket 已存在: {}", bucket);
        }
    }

    private static boolean bucketExists(S3Client s3Client, String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }
}
