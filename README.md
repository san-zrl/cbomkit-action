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