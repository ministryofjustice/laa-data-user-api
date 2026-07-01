# Local Dev Database

This directory contains everything you need to run a local instance of the PostgreSQL database for the `laa-data-user-api` application, fully populated with mock data. 

This isolated environment ensures that your local testing data is perfectly matched to the application's schema, without polluting the root workspace or requiring you to connect to a remote database.

## How it works
1. **`docker-compose.yml`**: Spins up a PostgreSQL 17 container.
2. **`schema_dump.sql`**: The official schema dump from `laa-landing-page`, which Docker automatically runs to set up the tables on initialization.
3. **`data-generator/`**: A python script that waits for PostgreSQL to become healthy, and then connects to it to insert 50 random users, firms, and app roles using the `Faker` library.

## Usage

To start the database:
```bash
cd local-dev-db
docker-compose up --build -d
```

To stop the database:
```bash
docker-compose down
```

To reset and regenerate fresh data:
```bash
docker-compose down -v
docker-compose up --build -d
```

## Connecting your App
By default, `laa-data-user-api` is configured to look for this database in `src/main/resources/application.yml` via:
- **Host**: localhost
- **Port**: 5432
- **User**: cptJXFyh0U
- **Password**: password
- **Database**: data_user_api
