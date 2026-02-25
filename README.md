# skills-graph

An **LLM-First Skills Graph** system powering a Recruitment Agency Platform, built with **Java 21**, **Spring Boot 3**, and **Spring AI**.

## Getting Started

### Prerequisites
- Java 21 (LTS)
- Maven 3.9+ (or use the included `./mvnw` wrapper)
- Docker & Docker Compose
- Neo4j 5 (via Docker Compose)

### Installation

```bash
./mvnw dependency:resolve
```

### Running

```bash
# Start infrastructure (Neo4j + PostgreSQL + Redis)
docker compose up -d

# Run Neo4j schema (constraints + indexes)
./mvnw spring-boot:run -Dspring-boot.run.arguments=--neo4j-schema

# Run PostgreSQL migrations (embedding + changelog tables)
./mvnw flyway:migrate

# Start the application
./mvnw spring-boot:run
```

### Testing

```bash
./mvnw test
```

This project uses **Java 21** with **Spring Boot 3**, **Spring AI**, **Neo4j 5** (graph storage), and **PostgreSQL** (vector embeddings + changelog).
