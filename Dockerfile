ARG KEYCLOAK_IMAGE="quay.io/keycloak/keycloak:20.0.2"

# Build protocoll mapper so that it always has the current version
FROM maven:3.8 as jdk-builder

WORKDIR /workspace
COPY ./protocol-mapper/ ./protocol-mapper/
COPY ./pom.xml .

RUN mvn clean package

FROM quay.io/adorsys/keycloak-config-cli:latest as keycloak-config-cli

# Build keycloak
FROM ${KEYCLOAK_IMAGE} as keycloak-builder

COPY --from=jdk-builder /workspace/protocol-mapper/target/keycloak-custom-protocol-mapper-example.jar /opt/keycloak/providers/
COPY --from=keycloak-config-cli /app/keycloak-config-cli.jar /opt/keycloak-config-cli.jar

RUN /opt/keycloak/bin/kc.sh build

# Create keycloak image
FROM ${KEYCLOAK_IMAGE}

WORKDIR /opt/keycloak
COPY --from=keycloak-builder /opt/keycloak/ .
CMD ["--verbose", "start-dev", "--http-enabled=true", "--http-relative-path=/", "--http-port=8080", "--hostname-strict=false", "--hostname-strict-https=false", "--import-realm"]
