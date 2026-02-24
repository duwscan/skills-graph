# skills-graph

An **LLM-First Skills Graph** system powering a Recruitment Agency Platform, built with **Java 21**, **Spring Boot 3**, and **Spring AI**.

## Getting Started

### Prerequisites
- Java 21 (LTS)
- Maven 3.9+ (or use the included `./mvnw` wrapper)
- Docker & Docker Compose

### Installation

```bash
./mvnw dependency:resolve
```

### Running

```bash
# Start infrastructure (PostgreSQL + Redis)
docker compose up -d

# Run database migrations
./mvnw flyway:migrate

# Start the application
./mvnw spring-boot:run
```

### Testing

```bash
./mvnw test
```

This project uses **Java 21** with **Spring Boot 3** and **Spring AI**.
