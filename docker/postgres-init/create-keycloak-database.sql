-- Keycloak keeps its users and settings in its own database next to ours.
-- Runs only when the postgres volume is created for the first time.
CREATE DATABASE keycloak OWNER price_tracker;
