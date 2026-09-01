package com.example.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ${{ .Inputs.description }}
 *
 * <p>The package is {@code com.example.app} rather than something derived from the
 * service name: a package rename is a single IDE refactor, whereas a templated
 * package name has to be threaded through every file and the build. Rename it
 * once, here and in {@code pom.xml}, if you want your own.
 */
@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
