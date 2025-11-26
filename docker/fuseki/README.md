# Apache Jena Fuseki Configuration

This directory contains the configuration for Apache Jena Fuseki 5.6.0 running as a Docker container.

## Overview

Fuseki is a SPARQL server that provides HTTP access to RDF data stores. It's configured to use TDB2 for storage, which provides high-performance RDF storage.

## Configuration

### Files

- `config.ttl` - Fuseki server configuration file
- `databases/` - Directory for TDB2 database storage (persisted via Docker volume)

### Services

The Fuseki server exposes the following SPARQL endpoints:

- **Query Endpoint**: `http://localhost:3030/cruise_kg/sparql`
- **Update Endpoint**: `http://localhost:3030/cruise_kg/update`
- **Query (alternative)**: `http://localhost:3030/cruise_kg/query`
- **Data Endpoint**: `http://localhost:3030/cruise_kg/data`

### Access

- **Fuseki UI**: http://localhost:3030
- **Admin Username**: `admin`
- **Admin Password**: `admin` (change in production!)

## Storage Backend

Currently configured to use **TDB2** (Triple Database 2), which is Apache Jena's native high-performance RDF storage system.

### MySQL Integration

Fuseki doesn't natively support MySQL as a storage backend. However, you have several options:

1. **Use TDB2** (current setup) - Fast, optimized for RDF, recommended for production
2. **Use MySQL via JDBC adapter** - Requires custom configuration and may have performance implications
3. **Hybrid approach** - Store metadata in MySQL, use TDB2 for RDF triples

### Migrating to MySQL

If you need to use MySQL as the storage backend:

1. **Option A: Custom JDBC Storage**
   - Implement a custom storage adapter using Jena's Storage API
   - Configure Fuseki to use the custom adapter
   - Requires significant development effort

2. **Option B: Data Migration**
   - Export RDF data from TDB2
   - Import into MySQL using a custom schema
   - Query via SPARQL-to-SQL translation layer

3. **Option C: Keep TDB2, Link to MySQL**
   - Keep RDF data in TDB2 (optimal performance)
   - Store relational data in MySQL
   - Link entities via shared identifiers

## Usage

### Starting Fuseki

```bash
docker-compose up -d fuseki
```

### Checking Status

```bash
curl http://localhost:3030/$/ping
```

### Querying via SPARQL

```bash
curl -X POST \
  -H "Content-Type: application/sparql-query" \
  -d "SELECT * WHERE { ?s ?p ?o } LIMIT 10" \
  http://localhost:3030/cruise_kg/sparql
```

### Updating Data

```bash
curl -X POST \
  -H "Content-Type: application/sparql-update" \
  -d "INSERT DATA { <http://example.org/subject> <http://example.org/predicate> <http://example.org/object> }" \
  http://localhost:3030/cruise_kg/update
```

## Application Integration

The Spring Boot application connects to Fuseki using the following configuration (from `application.yml`):

```yaml
knowledge:
  graph:
    endpoint: http://localhost:3030/cruise_kg/sparql
    update-endpoint: http://localhost:3030/cruise_kg/update
    query-endpoint: http://localhost:3030/cruise_kg/query
    username: admin
    password: admin
```

## Troubleshooting

### Fuseki won't start

1. Check Docker logs: `docker logs cruise_recommender_fuseki`
2. Verify port 3030 is not in use: `netstat -an | grep 3030`
3. Check disk space for TDB2 storage

### Connection issues

1. Verify Fuseki is running: `docker ps | grep fuseki`
2. Check network connectivity: `docker network inspect cruise_network`
3. Verify health check: `curl http://localhost:3030/$/ping`

### Performance issues

1. Increase JVM memory in `docker-compose.yml`: `JVM_ARGS: "-Xmx4g -Xms2g"`
2. Optimize TDB2 indexes
3. Consider using TDB2's union graph feature for better query performance

## References

- [Apache Jena Fuseki Documentation](https://jena.apache.org/documentation/fuseki2/)
- [Fuseki Docker Image](https://hub.docker.com/r/apache/jena-fuseki)
- [TDB2 Documentation](https://jena.apache.org/documentation/tdb2/)

