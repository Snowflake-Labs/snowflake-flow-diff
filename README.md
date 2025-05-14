# ![https://www.snowflake.com/](https://www.snowflake.com/wp-content/uploads/2023/02/Snowflake-Logo-e1682724848599.png) Snowflake Flow Diff for Apache NiFi

This action is brought to you by [Snowflake](https://www.snowflake.com/), don't hesitate to visit our website and reach out to us if you have questions about this action.

## Usage

When using the GitHub Flow Registry Client in NiFi to version control your flows, add the below file `.github/workflows/flowdiff.yml` to the repository into which flow definitions are versioned.

Whenever a pull request is opened, reopened or when a new commit is pushed to an existing pull request, this workflow will be triggered and will compare the modified flow in the pull request and will
automatically comment the pull request with a human readable description of the changes included in the pull request.

```yaml
name: Snowflake Flow Diff on Pull Requests
on:
  pull_request:
    types: [opened, reopened, synchronize]

jobs:
  execute_flow_diff:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write
    name: Executing Flow Diff
    steps:
      # checking out the code of the pull request (merge commit - if the PR is mergeable)
      - name: Checkout PR code
        uses: actions/checkout@v4
        with:
          ref: ${{ github.event.pull_request.head.ref }}
          fetch-depth: 0
          path: submitted-changes

      # getting the path of the flow definition that changed (only one expected for now)
      - name: Get changed files
        id: files
        run: |
          cd submitted-changes
          # Get changed files since the merge base
          changed_file=$(git diff --name-only $(git merge-base HEAD origin/${{ github.event.pull_request.base.ref }}) HEAD | grep -m 1 '\.json$' || true)
          # Set output (empty if no JSON file found)
          echo "all_changed_files=$changed_file" >> $GITHUB_OUTPUT

      # checking out the code without the change of the PR
      - name: Checkout original code
        uses: actions/checkout@v4
        with:
          fetch-depth: 2
          path: original-code
      - run: cd original-code && git checkout HEAD^

      # Running the diff
      - name: Snowflake Flow Diff
        uses: snowflake-labs/snowflake-flow-diff@v0
        id: flowdiff
        with:
          flowA: 'original-code/${{ steps.files.outputs.all_changed_files }}'
          flowB: 'submitted-changes/${{ steps.files.outputs.all_changed_files }}'
```

## Checkstyle

Optionally, it is possible to enable a checkstyle check on the new version of the flow. If some violations against NiFi best practices are found, a message will be added to the comment published on the pull request.

To enable checkstyle:

```yaml
      # Running the diff
      - name: Snowflake Flow Diff
        uses: snowflake-labs/snowflake-flow-diff@v0
        id: flowdiff
        with:
          flowA: 'original-code/${{ steps.files.outputs.all_changed_files }}'
          flowB: 'submitted-changes/${{ steps.files.outputs.all_changed_files }}'
          checkstyle: true
```

## Example

The GitHub Action will automatically publish a comment on the pull request with a comprehensive description of the changes between the flows of the two branches.
Here is an example of what the comment could look like:

```markdown
### Executing Snowflake Flow Diff for flow: `MyExample`

#### Checkstyle Violations
- Processor named `UpdateAttribute` is configured with 4 concurrent tasks

#### Flow Changes
- The destination of a connection has changed from `UpdateAttribute` to `InvokeHTTP`
- A self-loop connection `[success]` has been added on `UpdateAttribute`
- A connection `[success]` from `My Generate FlowFile Processor` to `UpdateAttribute` has been added
- A Processor of type `GenerateFlowFile` named `GenerateFlowFile` has been renamed from `GenerateFlowFile` to `My Generate FlowFile Processor`
- In Processor of type `GenerateFlowFile` named `GenerateFlowFile`, the Scheduling Strategy changed from `TIMER_DRIVEN` to `CRON_DRIVEN`
- A Parameter Context named `Test Parameter Context` has been added
- The Parameter Context `Test Parameter Context` with parameters `{addedParam=newValue}` has been added to the process group `TestingFlowDiff`
- A Processor of type `UpdateAttribute` named `UpdateAttribute` has been removed
- In Processor of type `GenerateFlowFile` named `GenerateFlowFile`, the Run Schedule changed from `1 min` to `* * * * * ?`
- A Parameter Context named `Another one to delete` has been added
- A Processor of type `UpdateAttribute` named `UpdateAttribute` has been added with the configuration [`ALL` nodes, `4` concurrent tasks, `0ms` run duration, `WARN` bulletin level, `TIMER_DRIVEN` (`0 sec`), `30 sec` penalty duration, `1 sec` yield duration] and the below properties:
  - `Store State` = `Do not store state`
  - `canonical-value-lookup-cache-size` = `100`

#### Bundle Changes
- The bundle `org.apache.nifi:nifi-standard-nar` has been changed from version `2.1.0` to version `2.2.0`
```
