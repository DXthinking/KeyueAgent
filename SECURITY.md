# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 0.1.0   | ✅ Yes             |

## Reporting a Vulnerability

🔒 **Please DO NOT report security vulnerabilities via public GitHub Issues.**

If you discover a security vulnerability, please email us at:

📧 **15154716+colddx@user.noreply.gitee.com**

Include in your report:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

We will respond within **48 hours** and work on a fix.

## Security Considerations

This project is a **demo / learning project** and is NOT production-ready.

If you deploy this in production, you MUST additionally:

- [ ] Set strong `DASHSCOPE_API_KEY` via environment variable (NEVER commit)
- [ ] Change all default passwords (MySQL `root/root`, Redis, MinIO `minioadmin`)
- [ ] Enable JWT/API-Key authentication at API Gateway
- [ ] Add rate limiting beyond Sentinel (e.g. nginx limit_req)
- [ ] Enable TLS for all inter-service communication
- [ ] Audit LLM Prompts for prompt injection risks
- [ ] Add audit logging for all money-related operations (refund/voucher)
- [ ] Restrict CORS allowed origins (currently `*`)
- [ ] Set Java security manager / sandbox for LLM-generated code execution (none currently)

## Known Limitations

| Component | Current state | Production hardening needed |
|----------|---------------|----------------------------|
| User Identity | Trusts `UserId` from request body | OAuth2 / JWT validation |
| Refund Approval | Soft threshold + MQ notification | 4-eye principle, SOP workflow |
| Voucher Issuance | Hardcoded threshold | Dynamic rules engine + audit |
| MQ Idempotency | Primary key only | Distributed dedup (Redis) |
| Secrets | `.env` / env vars | Vault / KMS integration |
| Audit Logs | Standard logs | WORM storage + compliance |
