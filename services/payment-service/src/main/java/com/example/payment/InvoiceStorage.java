package com.example.payment;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/** Generates an invoice file (TXT) and uploads it to S3 (LocalStack). */
@Component
class InvoiceStorage {

    private static final Logger log = LoggerFactory.getLogger(InvoiceStorage.class);

    private final S3Client s3;
    private final String bucket;
    private final boolean autoCreateBucket;

    InvoiceStorage(S3Client s3,
                   @Value("${app.s3.bucket}") String bucket,
                   @Value("${app.s3.auto-create-bucket:true}") boolean autoCreateBucket) {
        this.s3 = s3;
        this.bucket = bucket;
        this.autoCreateBucket = autoCreateBucket;
    }

    @PostConstruct
    void ensureBucketExists() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("S3 bucket '{}' available", bucket);
        } catch (S3Exception e) {
            if (!autoCreateBucket) {
                log.warn("S3 bucket '{}' unavailable (auto-create disabled). "
                        + "Run `terraform apply` in terraform/environments/localstack.", bucket);
                return;
            }
            log.info("S3 bucket '{}' does not exist - creating it (app.s3.auto-create-bucket=true)", bucket);
            s3.createBucket(b -> b.bucket(bucket));
        }
    }

    /** @return the invoice object key in S3. */
    String uploadInvoice(Payment payment, String product, int quantity) {
        String key = "invoices/order-%d.txt".formatted(payment.getOrderId());
        String body = render(payment, product, quantity);

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("text/plain; charset=utf-8")
                        .build(),
                RequestBody.fromString(body, StandardCharsets.UTF_8));

        log.info("Invoice saved in S3: s3://{}/{}", bucket, key);
        return key;
    }

    private String render(Payment p, String product, int quantity) {
        return """
                =========================================
                                 INVOICE
                =========================================
                Payment No   : %d
                Order No     : %d
                Date         : %s

                Item         : %s
                Qty          : %d
                -----------------------------------------
                TOTAL        : %s PLN
                Status       : %s
                =========================================
                """.formatted(
                p.getId(),
                p.getOrderId(),
                DateTimeFormatter.ISO_INSTANT.format(p.getCreatedAt()),
                product,
                quantity,
                p.getAmount(),
                p.getStatus());
    }
}
