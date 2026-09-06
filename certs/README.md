# Certificados autoassinados para Keycloak (producao simulacao)

## Gerar certificado

```powershell
# Na raiz do projeto
mkdir -Force certs

# Gerar certificado autoassinado (valido 1 ano)
New-SelfSignedCertificate `
    -DnsName "localhost" `
    -CertStoreLocation "Cert:\LocalMachine\My" `
    -NotAfter (Get-Date).AddYears(1) `
    -FriendlyName "keycloak-dev-cert"

# Exportar .pfx
$cert = Get-ChildItem Cert:\LocalMachine\My |
    Where-Object { $_.FriendlyName -eq "keycloak-dev-cert" }
Export-PfxCertificate `
    -Cert $cert `
    -FilePath "certs\tls.pfx" `
    -Password (ConvertTo-SecureString -String "change_me" -Force -AsPlainText)

# Converter para .crt + .key (requer openssl)
openssl pkcs12 -in certs/tls.pfx -out certs/tls.crt -nodes -nokeys
openssl pkcs12 -in certs/tls.pfx -out certs/tls.key -nodes -nocerts
```

## Notas

- O certificado e valido por 1 ano
- O Subject Alternative Name (SAN) e "localhost"
- Para producao real, usar certificado de CA confiavel (Let's Encrypt, etc.)
- Os arquivos .pfx, .crt e .key estao no .gitignore
