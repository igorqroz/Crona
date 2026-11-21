-- =====================================================================
-- Crona — V2: feriados nacionais de 2026 e 2027 (usados na CLT-05)
--
-- Primeira migração depois da V1: a V1 já rodou nos bancos, então NÃO é editada.
-- Qualquer mudança no banco, a partir de agora, vira um arquivo novo como este.
--
-- Feriados nacionais por lei (Leis 662/1949, 6.802/1980 e 14.759/2023).
-- Carnaval e Corpus Christi são ponto facultativo, não feriado nacional, então ficam de fora:
-- cada empresa pode cadastrar os seus (feriado com empresa_id).
--
-- Próximo passo (PAD-11): uma rotina anual que gera os feriados de cada ano, incluindo a
-- Sexta-feira Santa, que muda de data porque depende da Páscoa.
-- =====================================================================

INSERT INTO feriado (empresa_id, data, descricao) VALUES
    (NULL, DATE '2026-01-01', 'Confraternização Universal'),
    (NULL, DATE '2026-04-03', 'Sexta-feira Santa'),
    (NULL, DATE '2026-04-21', 'Tiradentes'),
    (NULL, DATE '2026-05-01', 'Dia do Trabalho'),
    (NULL, DATE '2026-09-07', 'Independência do Brasil'),
    (NULL, DATE '2026-10-12', 'Nossa Senhora Aparecida'),
    (NULL, DATE '2026-11-02', 'Finados'),
    (NULL, DATE '2026-11-15', 'Proclamação da República'),
    (NULL, DATE '2026-11-20', 'Dia Nacional de Zumbi e da Consciência Negra'),
    (NULL, DATE '2026-12-25', 'Natal'),
    (NULL, DATE '2027-01-01', 'Confraternização Universal'),
    (NULL, DATE '2027-03-26', 'Sexta-feira Santa'),
    (NULL, DATE '2027-04-21', 'Tiradentes'),
    (NULL, DATE '2027-05-01', 'Dia do Trabalho'),
    (NULL, DATE '2027-09-07', 'Independência do Brasil'),
    (NULL, DATE '2027-10-12', 'Nossa Senhora Aparecida'),
    (NULL, DATE '2027-11-02', 'Finados'),
    (NULL, DATE '2027-11-15', 'Proclamação da República'),
    (NULL, DATE '2027-11-20', 'Dia Nacional de Zumbi e da Consciência Negra'),
    (NULL, DATE '2027-12-25', 'Natal');
