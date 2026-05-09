FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN ./scripts/build.sh
EXPOSE 8080
CMD ["./scripts/run-server.sh"]
