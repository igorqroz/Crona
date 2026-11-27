package br.com.crona.colaborador;

import br.com.crona.compartilhado.PaginaResposta;
import br.com.crona.compartilhado.erro.ConflitoException;
import br.com.crona.compartilhado.erro.ParametroInvalidoException;
import br.com.crona.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.crona.compartilhado.erro.RegraVioladaException;
import br.com.crona.departamento.Departamento;
import br.com.crona.departamento.DepartamentoRepository;
import br.com.crona.empresa.EmpresaAtual;
import br.com.crona.empresa.EmpresaRepository;
import br.com.crona.seguranca.UsuarioAtual;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Regras do cadastro de colaboradores.
 */
@Service
public class ColaboradorService {

    private final ColaboradorRepository colaboradorRepository;
    private final DepartamentoRepository departamentoRepository;
    private final EmpresaRepository empresaRepository;
    private final EmpresaAtual empresaAtual;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventos;
    private final UsuarioAtual usuarioAtual;

    public ColaboradorService(ColaboradorRepository colaboradorRepository,
                              DepartamentoRepository departamentoRepository,
                              EmpresaRepository empresaRepository,
                              EmpresaAtual empresaAtual,
                              PasswordEncoder passwordEncoder,
                              ApplicationEventPublisher eventos,
                              UsuarioAtual usuarioAtual) {
        this.colaboradorRepository = colaboradorRepository;
        this.departamentoRepository = departamentoRepository;
        this.empresaRepository = empresaRepository;
        this.empresaAtual = empresaAtual;
        this.passwordEncoder = passwordEncoder;
        this.eventos = eventos;
        this.usuarioAtual = usuarioAtual;
    }

    // Campos pelos quais o front pode ordenar a lista. Lista fechada: o resto vira 400.
    private static final Set<String> ORDENACOES_PERMITIDAS = Set.of("nome", "email", "cargo", "dataAdmissao");

    @Transactional(readOnly = true)
    public PaginaResposta<ColaboradorResposta> pesquisar(String busca, Long departamentoId, Boolean ativo,
                                                          Pageable paginacao) {
        return pesquisar(busca, departamentoId, ativo, null, null, paginacao);
    }

    /**
     * Cadastros 2: também filtra por perfil (o select de Gestor pede GESTOR e ADMIN) e por gestor
     * (quantas pessoas alguém tem na equipe, antes de desativar).
     */
    @Transactional(readOnly = true)
    public PaginaResposta<ColaboradorResposta> pesquisar(String busca, Long departamentoId, Boolean ativo,
                                                          List<Perfil> perfis, Long gestorId, Pageable paginacao) {
        paginacao.getSort().forEach(ordem -> {
            if (!ORDENACOES_PERMITIDAS.contains(ordem.getProperty())) {
                throw new ParametroInvalidoException("Não é possível ordenar por \"" + ordem.getProperty()
                        + "\". Use: " + String.join(", ", ORDENACOES_PERMITIDAS.stream().sorted().toList()) + ".");
            }
        });

        // Busca vazia = "tudo": o LIKE '%%' casa com qualquer nome.
        String termo = busca == null ? "" : busca.trim();
        boolean semFiltrosNovos = (perfis == null || perfis.isEmpty()) && gestorId == null;
        var pagina = semFiltrosNovos
                ? colaboradorRepository.pesquisar(empresaAtual.id(), termo, departamentoId, ativo, paginacao)
                : colaboradorRepository.pesquisarComFiltros(empresaAtual.id(), termo, departamentoId, ativo,
                        perfis == null || perfis.isEmpty() ? List.of(Perfil.values()) : perfis, gestorId, paginacao);
        return PaginaResposta.de(pagina, ColaboradorResposta::de);
    }

    @Transactional(readOnly = true)
    public ColaboradorResposta buscar(Long id) {
        return ColaboradorResposta.de(buscarDaEmpresa(id));
    }

    @Transactional
    public ColaboradorResposta cadastrar(ColaboradorCadastro dados) {
        String email = normalizarEmail(dados.email());
        if (colaboradorRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("O e-mail " + email + " já está em uso.");
        }

        var colaborador = new Colaborador(
                empresaRepository.getReferenceById(empresaAtual.id()),
                dados.nome().trim(),
                email,
                passwordEncoder.encode(dados.senha()),   // a senha vira hash AQUI e nunca é guardada
                dados.perfil(),
                textoOuNulo(dados.cargo()),
                dados.dataAdmissao());

        colaborador.alocar(departamentoDaEmpresa(dados.departamentoId()), gestorValido(dados.gestorId(), null));
        var salvo = colaboradorRepository.save(colaborador);
        // Avisa quem gera os períodos aquisitivos. Roda na mesma transação: se der erro lá,
        // o cadastro inteiro é desfeito.
        eventos.publishEvent(new AdmissaoDefinida(salvo.getId()));
        return ColaboradorResposta.de(salvo);
    }

    @Transactional
    public ColaboradorResposta atualizar(Long id, ColaboradorAtualizacao dados) {
        var colaborador = buscarDaEmpresa(id);

        String email = normalizarEmail(dados.email());
        if (colaboradorRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflitoException("O e-mail " + email + " já está em uso.");
        }

        // BUG-06: ninguém muda o próprio perfil. Um ADMIN que tirasse de si o perfil poderia deixar a empresa
        // sem ninguém para cuidar dos cadastros (no seed, o Rafael é o único ADMIN). Outro ADMIN pode mudar.
        if (souEu(id) && dados.perfil() != colaborador.getPerfil()) {
            throw new RegraVioladaException("Você não pode mudar o próprio perfil. Peça para outra pessoa do RH.");
        }

        // Quem tem equipe não pode virar COLABORADOR: a equipe ficaria com um "gestor" sem perfil de gestor.
        if (!dados.perfil().podeGerenciarEquipe() && colaboradorRepository.existsByGestorIdAndAtivoTrue(id)) {
            throw new RegraVioladaException(colaborador.getNome()
                    + " é gestor de outras pessoas. Transfira a equipe antes de mudar o perfil.");
        }

        boolean admissaoMudou = !dados.dataAdmissao().equals(colaborador.getDataAdmissao());
        colaborador.atualizarDados(dados.nome().trim(), email, dados.perfil(),
                textoOuNulo(dados.cargo()), dados.dataAdmissao());
        colaborador.alocar(departamentoDaEmpresa(dados.departamentoId()), gestorValido(dados.gestorId(), colaborador));
        if (admissaoMudou) {
            eventos.publishEvent(new AdmissaoDefinida(id));
        }
        return ColaboradorResposta.de(colaborador);
    }

    @Transactional
    public ColaboradorResposta desativar(Long id) {
        var colaborador = buscarDaEmpresa(id);
        // BUG-06: ninguém tranca a si mesmo para fora do Crona.
        if (souEu(id)) {
            throw new RegraVioladaException("Você não pode desativar o próprio acesso. Peça para outra pessoa do RH.");
        }
        if (colaboradorRepository.existsByGestorIdAndAtivoTrue(id)) {
            throw new RegraVioladaException(colaborador.getNome()
                    + " é gestor de outras pessoas. Transfira a equipe antes de desativar.");
        }
        colaborador.desativar();
        return ColaboradorResposta.de(colaborador);
    }

    /** O colaborador {@code id} é quem está logado? */
    private boolean souEu(Long id) {
        return id.equals(usuarioAtual.id());
    }

    @Transactional
    public ColaboradorResposta reativar(Long id) {
        var colaborador = buscarDaEmpresa(id);
        colaborador.reativar();
        return ColaboradorResposta.de(colaborador);
    }

    // ------------------------------------------------------------------ regras auxiliares

    private Colaborador buscarDaEmpresa(Long id) {
        return colaboradorRepository.findByIdAndEmpresaId(id, empresaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador " + id + " não encontrado."));
    }

    /** O departamento é opcional, mas, se vier, tem que ser da mesma empresa. */
    private Departamento departamentoDaEmpresa(Long departamentoId) {
        if (departamentoId == null) {
            return null;
        }
        return departamentoRepository.findByIdAndEmpresaId(departamentoId, empresaAtual.id())
                .orElseThrow(() -> new RegraVioladaException("Departamento " + departamentoId + " não encontrado."));
    }

    /**
     * O gestor é opcional, mas, se vier, precisa:
     * 1. ser da mesma empresa;  2. estar ativo;  3. ter perfil de gestor ou admin;
     * 4. não ser a própria pessoa;  5. não criar um ciclo (A gerencia B, que gerencia A).
     *
     * @param colaborador quem está sendo alterado (null no cadastro, porque ainda não existe)
     */
    private Colaborador gestorValido(Long gestorId, Colaborador colaborador) {
        if (gestorId == null) {
            return null;
        }
        if (colaborador != null && gestorId.equals(colaborador.getId())) {
            throw new RegraVioladaException("Um colaborador não pode ser gestor de si mesmo.");
        }

        var gestor = colaboradorRepository.findByIdAndEmpresaId(gestorId, empresaAtual.id())
                .orElseThrow(() -> new RegraVioladaException("Gestor " + gestorId + " não encontrado."));
        if (!gestor.isAtivo()) {
            throw new RegraVioladaException(gestor.getNome() + " está desativado e não pode ser gestor.");
        }
        if (!gestor.getPerfil().podeGerenciarEquipe()) {
            throw new RegraVioladaException(gestor.getNome() + " tem perfil COLABORADOR e não pode ser gestor.");
        }

        // Sobe a cadeia de gestores: se em algum momento encontrar o próprio colaborador, é um ciclo.
        if (colaborador != null) {
            var visitados = new HashSet<Long>();
            for (var acima = gestor; acima != null && visitados.add(acima.getId()); acima = acima.getGestor()) {
                if (acima.getId().equals(colaborador.getId())) {
                    throw new RegraVioladaException("Esse gestor faz parte da equipe de " + colaborador.getNome()
                            + ". A hierarquia ficaria em círculo.");
                }
            }
        }
        return gestor;
    }

    private static String normalizarEmail(String email) {
        // Locale.ROOT: evita surpresas de idioma ao converter para minúsculas.
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
