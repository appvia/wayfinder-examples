# syntax=docker/dockerfile:1.6
#
# The Lambda function's container image. The BuildKit cache mount keeps Maven's
# local repository between builds, so changing one file does not download the
# world again — which is what makes the deploy loop worth using while iterating.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress dependency:go-offline

ARG RELEASE=dev
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress package -DskipTests -Drelease=${RELEASE}

# AWS's Java 21 Lambda base image loads classes from ${LAMBDA_TASK_ROOT} and
# jars from ${LAMBDA_TASK_ROOT}/lib.
FROM public.ecr.aws/lambda/java:21
COPY --from=build /src/target/classes ${LAMBDA_TASK_ROOT}
COPY --from=build /src/target/dependency ${LAMBDA_TASK_ROOT}/lib
CMD ["com.example.orders.Handler::handleRequest"]
