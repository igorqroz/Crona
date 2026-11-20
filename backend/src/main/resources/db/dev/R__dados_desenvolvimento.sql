-- =====================================================================
-- Dados de DESENVOLVIMENTO. Só rodam no perfil "dev" (ver application-dev.yml).
-- Nunca chegam em produção, porque a pasta db/dev só entra no Flyway nesse perfil.
--
-- "R__" = migração REPETÍVEL: o Flyway roda de novo sempre que o arquivo muda.
-- Por isso cada INSERT usa ON CONFLICT DO NOTHING: rodar duas vezes não duplica nada.
-- =====================================================================

-- Empresa de demonstração (CNPJ fictício, só para testes)
INSERT INTO empresa (razao_social, nome_fantasia, cnpj)
VALUES ('Empresa Demonstração Ltda', 'Empresa Demo', '11222333000181')
ON CONFLICT (cnpj) DO NOTHING;

-- Regras da empresa com os valores padrão
INSERT INTO configuracao_ferias (empresa_id, percentual_minimo_trabalhando)
SELECT id, 70 FROM empresa WHERE cnpj = '11222333000181'
ON CONFLICT (empresa_id) DO NOTHING;

-- Alguns departamentos para a API já ter o que listar
INSERT INTO departamento (empresa_id, nome)
SELECT e.id, d.nome
FROM empresa e
CROSS JOIN (VALUES ('Engenharia'), ('Financeiro'), ('Comercial'), ('Suporte'), ('RH')) AS d (nome)
WHERE e.cnpj = '11222333000181'
ON CONFLICT (empresa_id, nome) DO NOTHING;

-- Colaboradores de exemplo (os mesmos nomes do redesign).
-- SENHA DE TODOS: crona123  (só para desenvolvimento)
-- crypt(..., gen_salt('bf')) gera um hash BCrypt, o mesmo formato que o Java usa.
-- O pgcrypto é uma extensão do PostgreSQL; aqui ela só é usada nos dados de teste.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- A ordem importa: o gestor precisa existir antes de quem ele gerencia.
INSERT INTO colaborador (empresa_id, departamento_id, gestor_id, nome, email, senha_hash, perfil, cargo, data_admissao)
SELECT e.id, d.id, NULL, 'Rafael Costa', 'rafael.costa@demo.crona', crypt('crona123', gen_salt('bf')),
       'ADMIN', 'Gestor de RH', DATE '2019-03-11'
FROM empresa e JOIN departamento d ON d.empresa_id = e.id AND d.nome = 'RH'
WHERE e.cnpj = '11222333000181'
ON CONFLICT (lower(email)) DO NOTHING;

INSERT INTO colaborador (empresa_id, departamento_id, gestor_id, nome, email, senha_hash, perfil, cargo, data_admissao)
SELECT e.id, d.id, NULL, 'Roberto Neves', 'roberto.neves@demo.crona', crypt('crona123', gen_salt('bf')),
       'GESTOR', 'Coordenador de Engenharia', DATE '2020-06-01'
FROM empresa e JOIN departamento d ON d.empresa_id = e.id AND d.nome = 'Engenharia'
WHERE e.cnpj = '11222333000181'
ON CONFLICT (lower(email)) DO NOTHING;

INSERT INTO colaborador (empresa_id, departamento_id, gestor_id, nome, email, senha_hash, perfil, cargo, data_admissao)
SELECT e.id, d.id, g.id, x.nome, x.email, crypt('crona123', gen_salt('bf')), 'COLABORADOR', x.cargo, x.admissao
FROM empresa e
JOIN departamento d ON d.empresa_id = e.id AND d.nome = 'Engenharia'
JOIN colaborador g ON lower(g.email) = 'roberto.neves@demo.crona'
CROSS JOIN (VALUES
    ('Ana Lima',     'ana.lima@demo.crona',     'Desenvolvedora',  DATE '2024-02-01'),
    ('Bruno Teles',  'bruno.teles@demo.crona',  'Desenvolvedor',   DATE '2023-08-14'),
    ('Marina Jorge', 'marina.jorge@demo.crona', 'Analista de QA',  DATE '2022-05-02'),
    ('Lucas Prado',  'lucas.prado@demo.crona',  'Desenvolvedor',   DATE '2021-11-08')
) AS x (nome, email, cargo, admissao)
WHERE e.cnpj = '11222333000181'
ON CONFLICT (lower(email)) DO NOTHING;
