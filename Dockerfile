# syntax=docker/dockerfile:1
# =========================================================================
# TicketsCenter — Production Dockerfile
# Base: Apache Tomcat 11 on Java 25 Runtime
# =========================================================================

# Stage 1: Build & Package WAR
FROM maven:3-eclipse-temurin-25 AS builder
WORKDIR /app

# 1. Tận dụng Docker layer caching: tải trước dependencies khi pom.xml chưa đổi
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 2. Sao chép mã nguồn và đóng gói WAR, dùng dependencies đã lưu trong build layer
COPY src ./src
RUN mvn -B package -DskipTests

# Stage 2: Runtime with Apache Tomcat 11
FROM eclipse-temurin:25-jre
LABEL maintainer="TicketsCenter Team <devonxjz@ticketscenter.vn>"
LABEL description="TicketsCenter Event Ticketing Platform"

ENV CATALINA_HOME=/opt/tomcat
ENV PATH=$CATALINA_HOME/bin:$PATH
ENV TOMCAT_VERSION=11.0.4
ENV JAVA_OPTS="-Djava.awt.headless=true -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENV REDIS_CACHE_CATALOG_ENABLED=true

# Cài đặt curl cho healthcheck và thiết lập Tomcat 11 với fallback mirror
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /opt/tomcat \
    && (curl -sSL --retry 3 "https://archive.apache.org/dist/tomcat/tomcat-11/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz" \
        || curl -sSL --retry 3 "https://dlcdn.apache.org/tomcat/tomcat-11/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz") \
       | tar -xz -C /opt/tomcat --strip-components=1 \
    && rm -rf /opt/tomcat/webapps/* /opt/tomcat/temp/* /opt/tomcat/work/* \
    && chmod +x /opt/tomcat/bin/*.sh \
    && useradd -u 1001 -m -s /bin/bash tomcat \
    && chown -R tomcat:tomcat /opt/tomcat

WORKDIR /opt/tomcat

# Deploy WAR artifact
COPY --from=builder --chown=tomcat:tomcat /app/target/ticketscenter.war /opt/tomcat/webapps/ticketscenter.war

USER tomcat
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/ticketscenter/health/live || exit 1

CMD ["catalina.sh", "run"]
