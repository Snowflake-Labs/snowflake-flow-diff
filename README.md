# ![https://www.snowflake.com/](https://www.snowflake.com/wp-content/uploads/2023/02/Snowflake-Logo-e1682724848599.png) Snowflake Flow Diff for Apache NiFi

This action is brought to you by [Snowflake](https://www.snowflake.com/), don't hesitate to visit our website and reach out to us if you have questions about this action.

## Usage

When using the GitHub Flow Registry Client in NiFi to version control your flows, add the below file `.github/workflows/flowdiff.yml` to the repository into which flow definitions are versioned.

Whenever a pull request is opened, reopened or when a new commit is pushed to an existing pull request, this workflow will be triggered and will compare the modified flow(s) in the pull request and will
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

      # getting the path of the flow definition(s) that changed
      - name: Get changed files
        id: files
        run: |
          cd submitted-changes

          # 1. grab every changed connector JSON
          files=$(git diff --name-only $(git merge-base HEAD origin/${{ github.event.pull_request.base.ref }}) HEAD | grep '\.json$')

          # 2. make it a comma-separated list (no trailing comma)
          bare=$(echo "$files" | tr '\n' ',' | sed 's/,$//')

          # 3. prefix for original-code (flowA)
          flowA=$(echo "$bare" | sed 's|[^,]\+|original-code/&|g')

          # 4. prefix for submitted-changes (flowB)
          flowB=$(echo "$bare" | sed 's|[^,]\+|submitted-changes/&|g')

          # 5. export both as outputs
          echo "flowA=$flowA" >> $GITHUB_OUTPUT
          echo "flowB=$flowB" >> $GITHUB_OUTPUT

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
          flowA: ${{ steps.files.outputs.flowA }}
          flowB: ${{ steps.files.outputs.flowB }}
```

Note - you may want to change `grep  '\.json$'` with a more specific pattern to match your specific requirements.

## Checkstyle

Optionally, it is possible to enable a checkstyle check on the new version of the flow. If some violations against NiFi best practices are found, a message will be added to the comment published on the pull request.

To enable checkstyle:

```yaml
      # Running the diff
      - name: Snowflake Flow Diff
        uses: snowflake-labs/snowflake-flow-diff@v0
        id: flowdiff
        with:
          flowA: ${{ steps.files.outputs.flowA }}
          flowB: ${{ steps.files.outputs.flowB }}
          checkstyle: true
          # optional: path to YAML configuration of the rules
          checkstyle-rules: submitted-changes/.github/checkstyle/checkstyle-rules.yaml
```

The YAML file can be used to include or exclude specific rules and to configure rule parameters. For example:

```yaml
include:
  - concurrentTasks
rules:
  concurrentTasks:
    parameters:
      limit: 2
    overrides:
      ".*sql-connector":
        limit: 4
    exclude:
      - "Ignore.*"
```

At the root level, if `include` is specified, only those rules will be executed. If `exclude` is specified AND `include` is not specified, all rules except the ones specified will be executed. For each rule, it is possible to specify default values for parameters (when supported by the rule, see below), to override parameters for specific flows using a regular expression against the flow name, and to exclude flows using a regular expression.

Available rules:
- `concurrentTasks` to check the number of concurrent tasks and define an upper limit (parameter: `limit`, default value is 2)
- `snapshotMetadata` to check that the flow snapshot metadata is present in the file
- `emptyParameter` to check that no parameter is set to an empty string
- `defaultParameters` to check parameters with a default value (parameter: `defaultParameters`, comma-separated list of parameter names having an expected default value)
- `unusedParameter` to check if all specified parameters are used in the flow
- `enforcePrioritizer` to check if all connections in the flow are set with the configured list of prioritizers (parameter: `prioritizers`, comma-separated list of expected prioritizers, example: `org.apache.nifi.prioritizer.FirstInFirstOutPrioritizer`)

## Example

The GitHub Action will automatically publish a comment on the pull request with a comprehensive description of the changes between the flows of the two branches.
Here is an example of what the comment could look like:

```markdown
### Executing Snowflake Flow Diff for flow: `MyExample`

#### Checkstyle Violations
> [!CAUTION]
> - Processor named `UpdateAttribute` is configured with 4 concurrent tasks

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
