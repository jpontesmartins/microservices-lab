package com.example.vendas.usuario.domain.port;

import com.example.vendas.usuario.domain.model.Usuario;

import java.util.Optional;

public interface UsuarioRepositoryPort {

    Optional<Usuario> buscarPorLogin(String login);

    Optional<Usuario> buscarPorId(Long id);

    Usuario salvar(Usuario usuario);
}
