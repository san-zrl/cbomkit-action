# CBOMkit-action

GitHub Action to generate CBOMs.

## Usage

```yaml
on:
  workflow_dispatch:

jobs:
  cbom-scan:
    runs-on: ubuntu-latest
    name: CBOM generation
    permissions:
      contents: write
      pull-requests: write
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
      - name: Create CBOM
        uses: PQCA/cbomkit-action@v1.0.0
        id: cbom
      # Allow you to persist CBOM after a job has completed, and share 
      # that CBOM with another job in the same workflow.
      - name: Create and publish CBOM artifact
        uses: actions/upload-artifact@v4
        with:
          name: "CBOM"
          path: ${{ steps.cbom.outputs.filename }}
```

## Supported languages and libraries

The current scanning capabilities of the CBOMkit are defined by the [Sonar Cryptography Plugin's](https://github.com/IBM/sonar-cryptography) supported languages 
and cryptographic libraries:

| Language | Cryptographic Library                                                                         | Coverage | 
|----------|-----------------------------------------------------------------------------------------------|----------|
| Java     | [JCA](https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html) | 100%     |
|          | [BouncyCastle](https://github.com/bcgit/bc-java) (*light-weight API*)                         | 100%[^1] |

[^1]: We only cover the BouncyCastle *light-weight API* according to [this specification](https://javadoc.io/static/org.bouncycastle/bctls-jdk14/1.75/specifications.html)

While the CBOMkit's scanning capabilities are currently bound to the Sonar Cryptography Plugin, the modular 
design of this plugin allows for potential expansion to support additional languages and cryptographic libraries in 
future updates.
