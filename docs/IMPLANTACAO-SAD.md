# Implantação no servidor SAD (10.222.1.245)

**Para quem executa:** alguém com acesso ao servidor. Este documento existe
porque o ambiente onde o código é escrito **não alcança** a rede 10.222.1.245 —
testado: `TCP 8888 inacessível`, e `10.0.0.0/8` não passa pelo proxy de saída.

---

## 0. Antes de tudo: trocar a chave SSH

A chave privada `sad` foi enviada por conversa e está registrada no histórico.
Ela é ed25519 **sem passphrase**, para `root`. Trate-a como comprometida:

```bash
# no servidor, remova a pública correspondente
ssh-keygen -y -f sad            # mostra a pública, para identificar a linha
vi ~/.ssh/authorized_keys       # apague a linha com comentário "sad"

# gere um par novo, COM passphrase, na sua máquina
ssh-keygen -t ed25519 -C "sad-$(date +%Y%m)" -f ~/.ssh/sad_novo
ssh-copy-id -i ~/.ssh/sad_novo.pub -p 8888 root@10.222.1.245
```

Chave sem passphrase para `root` é um único arquivo entre qualquer cópia dele e
o controle total da máquina. Se ela precisar existir, que exista com passphrase
e com escopo menor que `root` — ver §5.

---

## 1. O que o servidor precisa ter

```bash
docker --version && docker compose version
```

Se não houver Docker, instale-o. **Nada mais é necessário**: Java, Maven e
PostgreSQL rodam dentro dos contêineres.

> Se o servidor for offline (D-06 do plano), o `docker build` não vai alcançar o
> Maven Central. Nesse caso a imagem precisa ser construída onde há rede e
> carregada aqui com `docker save` / `docker load`, ou puxada de um registry
> interno.

---

## 2. Trazer o código

```bash
git clone <url-do-repositorio> /opt/sgdf
cd /opt/sgdf
git checkout claude/reconciliation-document-checklist-td0pw2
```

---

## 3. Configurar

```bash
cp .env.exemplo .env
```

Preencha no `.env`:

| Variável | Valor | Observação |
|---|---|---|
| `SGDF_JDBC_SENHA` | senha forte gerada | `openssl rand -base64 24` |
| `SGDF_BIND` | `0.0.0.0` | o proxy reverso está em outra máquina |
| `SGDF_PORTA` | `80` | porta do **host** |
| `SGDF_OIDC_ISSUER` | URL do seu IdP | **ver §4 — é o que bloqueia o uso** |
| `SGDF_INSTANCIA` | `sad-01` | aparece na trilha |

A aplicação **não roda como root** mesmo na porta 80: quem escuta a 80 é o
Docker, no host; dentro do contêiner o processo segue em 8080 como o usuário
`sgdf`. O CI verifica isso a cada commit.

---

## 4. O IdP é pré-requisito de USO, não de subida

Sem `SGDF_OIDC_ISSUER` apontando para um IdP real, a aplicação **sobe e
responde**, mas todo endpoint devolve **401** — inclusive o de varredura.
`negar é o padrão` (cap. 15.1) não tem exceção para "ainda não configuramos".

O que funciona sem IdP:

- `GET /saude/health` → `{"status":"UP"}` — é a única rota liberada, e de
  propósito: sem detalhe de componente, para não contar topologia
- o proxy reverso e o certificado, de ponta a ponta
- o agendador, que roda sem requisição HTTP

O que **não** funciona sem IdP: cadastro, painel, triagem e varredura.

Item 1.4 do plano de implantação: mapear os grupos do IdP aos 7 papéis, com MFA
nos que veem escopo profissional — e agora também no `ADMIN_SISTEMA`, que passou
a cadastrar regra (ADR-004).

---

## 5. Subir

```bash
cd /opt/sgdf
docker compose up -d app
```

Isso sobe, **nesta ordem**: banco → migração do esquema → aplicação. O `clamd`
fica de fora deste comando porque baixa ~1 GB e leva ~3 min para aquecer; suba-o
quando quiser a verificação antivírus:

```bash
docker compose up -d clamd
```

### Conferir

```bash
docker compose ps                       # app deve ficar (healthy)
curl -s localhost:80/saude/health       # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' localhost:80/api/indicadores   # 401
```

O `401` é o resultado **correto**: prova que a aplicação está no ar e que nega
sem credencial.

---

## 6. Apontar a varredura para a nuvem

Só depois que o IdP estiver de pé. No `.env`:

```
SGDF_WEBDAV_BASE=https://cloud.engesoftware.com.br/remote.php/dav/files/svc-sgdf-leitura
SGDF_WEBDAV_USUARIO=svc-sgdf-leitura
SGDF_WEBDAV_SENHA=...
```

`SGDF_WEBDAV_BASE` carrega **endereço e caminho-raiz do WebDAV**, incluindo o
`/remote.php/dav/files/<conta>` da OwnCloud/Nextcloud. O `pasta_origem` do
contrato é **relativo a essa base**:

```
/Departamento de Pessoal/FATURAMENTO
```

Isto não é cosmética. Se o trecho `/remote.php/dav/files/<conta>` morasse no
`pasta_origem`, atualizar a nuvem ou trocar a conta de serviço invalidaria a
pasta de **todo** contrato e o `caminho` de **todo** documento já registrado —
uma migração de dados sobre linhas que a trilha do cap. 16 já carimbou. Com o
prefixo na base, a troca é uma variável de ambiente. Ver ADR-005.

> **Atenção:** versões anteriores a esta descartavam em silêncio o caminho da
> base. Se você configurou `SGDF_WEBDAV_BASE` só com o host e pôs o caminho DAV
> dentro do `pasta_origem`, **os dois precisam mudar juntos** — do contrário a
> requisição sai com o prefixo duplicado e o PROPFIND devolve 404.

A conta deve ser **somente-leitura** (item 1.3 do plano). Depois:

```bash
docker compose up -d --force-recreate app
```

---

## 7. Atualizar

```bash
cd /opt/sgdf && git pull
docker compose up -d --build app
```

A migração roda sozinha e é idempotente: banco vazio aplica tudo, banco completo
não faz nada, banco **parcialmente** migrado **recusa** e manda olhar — em vez de
reaplicar por cima e deixar um estado pior.

---

## 8. O que ainda não está resolvido

| Item | Situação |
|---|---|
| **IdP** (1.4) | sem ele ninguém autentica |
| **Cofre** (1.2) | as senhas estão no `.env`, não num cofre |
| **PITR + teste de restauração** (1.5) | o banco é um contêiner com volume local, sem PITR |
| **Bucket imutável** (1.6) | o book não tem onde ser publicado de forma imutável |
| **RIPD** (B4) | pré-requisito para tratar dado pessoal real |
| **B1, B2, B3** | sem elas, `AberturaDeCiclos` abre **0 de 12** ciclos |

Este servidor sobe a aplicação. Ele **não** a torna apta a receber dado real de
produção — os quatro primeiros itens são de infraestrutura e os dois últimos são
decisões que não são de TI.
