.PHONY: build build-auth build-authz build-gateway up down logs clean keys

## Build all services
build: build-auth build-authz build-gateway

build-auth:
	cd auth-service && mvn clean package -DskipTests

build-authz:
	cd authorization-service && mvn clean package -DskipTests

build-gateway:
	cd api-gateway && mvn clean package -DskipTests

## Docker Compose
up:
	docker compose up -d

down:
	docker compose down

logs:
	docker compose logs -f

## Generate RSA keys for JWT
keys:
	./scripts/keys/generate-rsa-keys.sh --output-dir ./keys

## Clean local builds
clean:
	cd auth-service && mvn clean
	cd authorization-service && mvn clean
	cd api-gateway && mvn clean
