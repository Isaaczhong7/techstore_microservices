# TechStore Microservices

A distributed e-commerce sample built with Spring Boot microservices, Kafka event streaming, PostgreSQL persistence, and a React frontend. The project demonstrates an event-driven architecture where product, inventory, order, payment, and lookup concerns are split into separate services.Eventually deloyed on AWS EKS with a CI/CD pipeline using GitHub Actions.

## Architecture overview

The system is composed of the following services:

- Product Service — manages product data and emits product events
- Inventory Service — tracks stock levels and reacts to inventory-related events
- Order Service — creates and processes orders
- Payment Service — handles payment operations
- Lookup Service — aggregates data from product and inventory for read-side queries
- Frontend — React UI for catalog and ordering flows
- Kafka + Schema Registry — asynchronous communication and event schema management
- PostgreSQL databases — one database per service

## Service map

| Service | Port | Purpose |
| --- | ---: | --- |
| Frontend | 3000 | React admin/catalog UI |
| Order Service | 8080 | Order orchestration |
| Product Service | 8081 | Product management |
| Inventory Service | 8082 | Stock management |
| Payment Service | 8083 | Payment processing |
| Lookup Service | 8084 | Read model / cross-service queries |
| Kafka | 9092 | Event broker |
| Schema Registry | 8070 | Avro schema registry |
| Order DB | 5433 | Order database |
| Inventory DB | 5434 | Inventory database |
| Payment DB | 5435 | Payment database |
| Product DB | 5436 | Product database |
| Lookup DB | 5437 | Lookup database |

## Tech stack

- Java 17 / Spring Boot 4.x
- Maven wrappers in each service
- PostgreSQL 17
- Apache Kafka + Confluent Schema Registry
- Avro serialization
- React + Vite + TypeScript
- Docker + Docker Compose

## Repository structure

```text
.
├── docker-compose.yml
├── .env.example
├── frontend/
├── inventory-service/
├── lookup-service/
├── order-service/
├── payment-service/
├── product-service/
├── schemas/
├── k8s/
└── README.md
```

## Prerequisites

- Docker Desktop or Docker Engine
- Docker Compose
- Java 17+
- Node.js 18+
- npm

## Quick start

1. Copy the environment template:

```bash
cp .env.example .env
```

2. Start the infrastructure and services:

```bash
docker compose up --build
```

3. Access the app:

- Frontend: http://localhost:3000
- Product service: http://localhost:8081
- Inventory service: http://localhost:8082
- Order service: http://localhost:8080
- Lookup service: http://localhost:8084
- Kafka: localhost:9092
- Schema Registry: http://localhost:8070

## Running services locally

Each Spring Boot service includes a Maven wrapper and can be started directly if you prefer not to use Docker for a given service.

Example:

```bash
cd product-service
./mvnw spring-boot:run
```

Use the same pattern for the other services:

```bash
cd order-service
./mvnw spring-boot:run
```

```bash
cd inventory-service
./mvnw spring-boot:run
```

```bash
cd payment-service
./mvnw spring-boot:run
```

```bash
cd lookup-service
./mvnw spring-boot:run
```

The frontend can be started separately with:

```bash
cd frontend
npm install
npm run dev
```

## Configuration

The root `.env` file controls Postgres credentials and service URLs. A sample is provided in `.env.example`.

Example:

```env
POSTGRES_USER=postgres
POSTGRES_PASSWORD=change-me
DB_USERNAME=postgres
DB_PASSWORD=change-me
PRODUCT_SERVICE_URL=http://product-service:8081
INVENTORY_SERVICE_URL=http://inventory-service:8082
ORDER_SERVICE_URL=http://order-service:8080
LOOKUP_SERVICE_URL=http://lookup-service:8084
```

## Notes on the event-driven setup

- Kafka is used for asynchronous communication between services.
- Schema Registry stores Avro schemas for compatibility.
- Services use local PostgreSQL instances configured in Docker Compose.
- Health checks are configured for the product, inventory, and lookup-related service startup chain.

## Useful commands

```bash
# View running containers
docker compose ps

# Follow logs
docker compose logs -f

# Stop everything
docker compose down

# Stop everything and remove volumes
docker compose down -v
```

## License

This project is intended as a demonstration project and does not include an explicit license file.
