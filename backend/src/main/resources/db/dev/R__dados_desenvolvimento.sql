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
