package com.example.app;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reports what this service is and which release is running. */
@RestController
public class ServiceController {

  /**
   * Set at build time by CI: the git tag for a release, the short SHA otherwise, so a running pod
   * can always be traced back to a commit.
   */
  @Value("${RELEASE:dev}")
  private String release;

  /**
   * Set by the Helm chart from the storage component's output. The workload reaches the bucket
   * through its own cloud identity, so there are no credentials to read here.
   */
  @Value("${BUCKET_NAME:}")
  private String bucketName;

  @GetMapping("/")
  public Map<String, String> root() {
    return Map.of(
        "service", "${{ .Inputs.serviceName }}",
        "release", release,
        "bucket", bucketName);
  }
}
