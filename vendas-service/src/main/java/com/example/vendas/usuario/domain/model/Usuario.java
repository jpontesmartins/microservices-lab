package com.example.vendas.usuario.domain.model;

public class Usuario {

    private Long id;
    private String login;
    private String nome;
    private String email;

    private Usuario() {
    }

    public Usuario(Long id, String login, String nome, String email) {
        this.id = id;
        this.login = login;
        this.nome = nome;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }
}
