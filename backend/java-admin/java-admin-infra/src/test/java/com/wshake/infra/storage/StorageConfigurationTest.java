package com.wshake.infra.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3 启动时自动建桶。
 */
class StorageConfigurationTest {

    @Test
    void ensureBucket_exists_skipsCreate() {
        S3Client s3 = mock(S3Client.class);
        when(s3.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        StorageConfiguration.ensureBucketExists(s3, "java-admin");

        verify(s3, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void ensureBucket_missing_creates() {
        S3Client s3 = mock(S3Client.class);
        when(s3.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("missing").build());
        when(s3.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());

        StorageConfiguration.ensureBucketExists(s3, "java-admin");

        verify(s3).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void ensureBucket_404_creates() {
        S3Client s3 = mock(S3Client.class);
        when(s3.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder()
                        .statusCode(404)
                        .message("not found")
                        .build());
        when(s3.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());

        StorageConfiguration.ensureBucketExists(s3, "java-admin");

        verify(s3).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void ensureBucket_alreadyOwned_isOk() {
        S3Client s3 = mock(S3Client.class);
        when(s3.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("missing").build());
        when(s3.createBucket(any(CreateBucketRequest.class)))
                .thenThrow(BucketAlreadyOwnedByYouException.builder()
                        .message("owned")
                        .build());

        StorageConfiguration.ensureBucketExists(s3, "java-admin");
    }

    @Test
    void ensureBucket_otherError_propagates() {
        S3Client s3 = mock(S3Client.class);
        when(s3.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder()
                        .statusCode(403)
                        .message("forbidden")
                        .build());

        assertThatThrownBy(() -> StorageConfiguration.ensureBucketExists(s3, "java-admin"))
                .isInstanceOf(S3Exception.class)
                .hasMessageContaining("forbidden");
    }
}
