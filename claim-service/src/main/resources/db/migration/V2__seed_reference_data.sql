-- Données de référence initiales (BF16 : exemples Facturation, Support technique, Livraison).
-- Chaque catégorie est traitée par le service du même nom. Modifiables ensuite par l'admin (BF22).

INSERT INTO services (name, description, created_at) VALUES
    ('Facturation', 'Factures, paiements, remboursements', CURRENT_TIMESTAMP),
    ('Support technique', 'Pannes, bugs, accès aux services', CURRENT_TIMESTAMP),
    ('Livraison', 'Retards, colis perdus ou endommagés', CURRENT_TIMESTAMP);

INSERT INTO categories (name, description, service_id, created_at)
    SELECT 'Facturation', 'Erreur de facture, prélèvement, remboursement', id, CURRENT_TIMESTAMP
    FROM services WHERE name = 'Facturation';
INSERT INTO categories (name, description, service_id, created_at)
    SELECT 'Support technique', 'Problème technique, panne, accès au compte', id, CURRENT_TIMESTAMP
    FROM services WHERE name = 'Support technique';
INSERT INTO categories (name, description, service_id, created_at)
    SELECT 'Livraison', 'Retard, colis perdu ou endommagé', id, CURRENT_TIMESTAMP
    FROM services WHERE name = 'Livraison';
