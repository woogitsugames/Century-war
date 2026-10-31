FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :server:installDist -PserverOnly=true --no-daemon --console=plain

FROM eclipse-temurin:21-jre-noble
RUN groupadd -r century && useradd -r -g century century \
    && mkdir -p /var/lib/cw && chown -R century:century /var/lib/cw
COPY --from=build /workspace/server/build/install/server /opt/century-war
ENV CW_DATABASE_PATH=/var/lib/cw/leaderboards.db
ENV PORT=8080
VOLUME ["/var/lib/cw"]
USER century
EXPOSE 8080
ENTRYPOINT ["/opt/century-war/bin/server"]
