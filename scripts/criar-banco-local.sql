-- =====================================================================
-- Cria o banco e o usuário do Crona no PostgreSQL LOCAL (desenvolvimento).
-- Rodar uma única vez, conectado como o superusuário "postgres":
--
--   psql -U postgres -f scripts/criar-banco-local.sql
--
-- A senha abaixo é só para a sua máquina. Em produção, a senha vem de
-- variável de ambiente e nunca fica no repositório.
-- =====================================================================

CREATE ROLE crona WITH LOGIN PASSWORD 'crona_dev';

-- O dono do banco é o usuário crona, então ele pode criar as tabelas (via Flyway).
-- TEMPLATE template0 garante o UTF8 (acentos) mesmo se o padrão do Windows for outro.
CREATE DATABASE crona OWNER crona ENCODING 'UTF8' TEMPLATE template0;
