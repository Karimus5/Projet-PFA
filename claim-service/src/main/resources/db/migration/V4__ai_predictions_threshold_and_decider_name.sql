-- Étape 7c : seuil de confiance utilisé par le modèle pour chaque prédiction (renvoyé aux agents et
-- admins dans aiPrediction.threshold) et nom de l'auteur de la décision (confirmation / correction).
-- Compatible PostgreSQL et H2. Les lignes existantes gardent NULL (seuil inconnu, nom inconnu).
--
-- decided_by_name : copie du nom de l'AGENT tel que saisi par l'admin lors du rattachement à un
-- service (table service_agents). claim-service ne connaît pas le nom des administrateurs (il
-- n'appelle pas auth-service) : NULL pour eux, seul le rôle est connu. Jamais d'e-mail.

ALTER TABLE ai_predictions ADD COLUMN threshold DOUBLE PRECISION;
ALTER TABLE ai_predictions ADD COLUMN decided_by_name VARCHAR(100);
ALTER TABLE ai_predictions ADD CONSTRAINT ck_ai_predictions_threshold
    CHECK (threshold IS NULL OR (threshold >= 0 AND threshold <= 1));
