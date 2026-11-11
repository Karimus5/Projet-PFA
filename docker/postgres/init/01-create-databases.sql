-- Exécuté automatiquement par l'image postgres au PREMIER démarrage (volume vide) uniquement.
-- Une base par microservice (cahier des charges §7). Pour un nouveau service, ajouter une ligne
-- sur le même modèle. Si le volume existe déjà, exécuter la ligne à la main (voir README).
-- Idempotent : une base déjà existante n'est pas recréée.

SELECT 'CREATE DATABASE auth_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth_db')\gexec
-- SELECT 'CREATE DATABASE claim_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'claim_db')\gexec
-- SELECT 'CREATE DATABASE notification_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notification_db')\gexec
-- SELECT 'CREATE DATABASE stats_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'stats_db')\gexec
