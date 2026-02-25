MVNW ?= ./mvnw

.PHONY: help up down reset run run-dev test clean verify migrate seed health package

help: ## Show available commands
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-12s\033[0m %s\n", $$1, $$2}'

up: ## Start local infrastructure (Postgres/Redis/Neo4j)
	docker compose up -d

down: ## Stop local infrastructure
	docker compose down

reset: ## Reset local infrastructure (remove volumes and restart)
	docker compose down -v
	docker compose up -d

run: ## Run API with default profile
	$(MVNW) spring-boot:run

run-dev: ## Run API with development profile
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=development

test: ## Run tests
	$(MVNW) test

clean: ## Clean build output
	$(MVNW) clean

verify: ## Clean and run tests
	$(MVNW) clean test

migrate: ## Run Flyway migrations
	$(MVNW) flyway:migrate

seed: ## Seed locale and root categories
	$(MVNW) spring-boot:run -Dspring-boot.run.arguments=--seed

health: ## Probe health endpoint
	curl -sS http://localhost:8080/actuator/health

package: ## Build jar (skip tests)
	$(MVNW) package -DskipTests
