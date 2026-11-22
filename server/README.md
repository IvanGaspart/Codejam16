# Occupancy server

The Java 21 Spring Boot API accepts scanner batches at `POST /api/batches` using the `X-API-Key` header. It maps Wi-Fi access points to floors, estimates scanner positions, and accounts for overlapping Bluetooth coverage. It stores batches and floor history in PostgreSQL. The website reads occupancy, history, scanner positions, and the live X-Ray constants through public GET endpoints. [API specification](../docs/SPEC.md#rest-api)

## Install and run

Install a JDK 21 and start PostgreSQL. For a fresh local database using Docker, run this once. The database name, user, and password match the application defaults.

```sh
docker run -d --name larpscan-db -p 127.0.0.1:5432:5432 \
  -e POSTGRES_DB=larpscan -e POSTGRES_USER=larpscan \
  -e POSTGRES_PASSWORD=larpscan postgres:16
```

Once PostgreSQL is ready, start the API from the repository root. The Gradle wrapper installs build dependencies, and the application creates the tables and loads the bundled floor and access-point data.

```sh
cd server
./gradlew bootRun
```

From another terminal, run `curl http://localhost:8080/api/buildings` and look for `trottier`. The local scanner key defaults to `dev-local-key`. Run `./gradlew test` from `server/` for the backend tests, which use an in-memory database.

## Deployment

On Render, select Docker, set the root directory to `server`, and provision PostgreSQL in the same region. Set `SPRING_DATASOURCE_URL` to the database’s JDBC URL and supply `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`. Set `LARP_API_KEY` on both server and scanners. Use `LARP_CORS_ORIGIN` for a custom website origin. Render supplies `PORT`. [Configuration and occupancy defaults](src/main/resources/application.yml)
