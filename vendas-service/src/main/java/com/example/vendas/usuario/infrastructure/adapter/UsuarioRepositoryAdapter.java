package com.example.vendas.usuario.infrastructure.adapter;

import com.example.vendas.usuario.domain.model.Usuario;
import com.example.vendas.usuario.domain.port.UsuarioRepositoryPort;
import com.example.vendas.usuario.entities.UsuarioEntity;
import com.example.vendas.usuario.infrastructure.repository.UsuarioJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UsuarioRepositoryAdapter implements UsuarioRepositoryPort {

    private final UsuarioJpaRepository jpaRepository;

    public UsuarioRepositoryAdapter(UsuarioJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Usuario> buscarPorLogin(String login) {
        return jpaRepository.findByLogin(login).map(this::toDomain);
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        UsuarioEntity entity = toEntity(usuario);
        jpaRepository.save(entity);
        return new Usuario(entity.getId(), entity.getLogin(), entity.getNome(), entity.getEmail());
    }

    private Usuario toDomain(UsuarioEntity entity) {
        return new Usuario(entity.getId(), entity.getLogin(), entity.getNome(), entity.getEmail());
    }

    private UsuarioEntity toEntity(Usuario usuario) {
        return new UsuarioEntity(usuario.getId(), usuario.getLogin(), usuario.getNome(), usuario.getEmail());
    }
}
