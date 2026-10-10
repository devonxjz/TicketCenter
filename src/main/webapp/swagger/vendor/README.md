# Swagger UI assets

Vendored from [swagger-api/swagger-ui v5.33.1](https://github.com/swagger-api/swagger-ui/releases/tag/v5.33.1), under the included Apache-2.0 LICENSE. Only CSS and the bundle are needed; no CDN, npm runtime or new Maven dependency. Validator service and authorization persistence are disabled by the application initializer.

SHA-256:

| File | Hash |
|---|---|
| swagger-ui-bundle.js | `050bc415ee7048dcd881682678f720264e7da5e373f7461d7c58c755305255f7` |
| swagger-ui.css | `1ac324f7dcd27e4b9386b4bd6421271ec147e922a22c05ba24b11515e9aa6321` |
| LICENSE | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |

To upgrade, replace files from one reviewed upstream tag, retain LICENSE, update these hashes, and rerun contract, initializer and browser checks. Source maps are not shipped; the upstream bundle's sourceMappingURL is a development-only reference.
