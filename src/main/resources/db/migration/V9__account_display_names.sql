-- Expansiva: conserva identidades, roles y membresías; nombre solo del perfil verificado.
ALTER TABLE user_account ADD COLUMN display_name varchar(200) NOT NULL DEFAULT '';
