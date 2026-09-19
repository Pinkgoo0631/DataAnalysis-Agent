[中文](./QUICK_START.md) | English

# Quick Start

This document will guide you through the installation, configuration, and first run of DataAgent.

## Prerequisites

- **JDK**: 17 or higher
- **MySQL**: 5.7 or higher
- **Node.js**: 22 or higher
- **pnpm**: 11 or higher
- **Docker**: Required to run the default Chroma store locally and when workflows execute Python steps
- **Vector Database**: Chroma by default (local port `8000`)

## 1. Business Database Preparation

You can get test tables and data from the project repository:

Files are located in: `data-agent-management/src/main/resources/sql`, which contains 4 files:
- `schema.sql` - Table structure for features
- `data.sql` - Data for features
- `product_schema.sql` - Sample data table structure
- `product_data.sql` - Sample data

Import the tables and data into your MySQL database.

```bash
# Example: Import using MySQL command line
mysql -u root -p your_database < data-agent-management/src/main/resources/sql/schema.sql
mysql -u root -p your_database < data-agent-management/src/main/resources/sql/data.sql
mysql -u root -p your_database < data-agent-management/src/main/resources/sql/product_schema.sql
mysql -u root -p your_database < data-agent-management/src/main/resources/sql/product_data.sql
```

## 2. Configuration

### 2.1 Configure Management Database

Create the Git-ignored local configuration file at the repository root, then fill in the database connection:

> Initialization behavior: the default is `spring.sql.init.mode: never`, so DataAgent does not
> create tables or insert sample data automatically. Run the SQL files above before the first
> start, or set `DATA_AGENT_DATASOURCE_SQL_INIT=always` only when sample initialization is
> explicitly required.

```bash
cp .env.example .env
```

```dotenv
DATA_AGENT_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/saa_data_agent?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
DATA_AGENT_DATASOURCE_USERNAME=replace-me
DATA_AGENT_DATASOURCE_PASSWORD=replace-me
DATA_AGENT_DATASOURCE_SQL_INIT=never
```

`application.yml` automatically imports the root `.env`; Git ignores this file.

### 2.2 Data Initialization Configuration

Auto initialization is disabled by default (`spring.sql.init.mode: never`).

> To change initialization behavior, see [Developer Guide - Database Initialization](DEVELOPER_GUIDE-en.md#8-database-initialization).

### 2.3 Configure Model

> If you need to manually manage model dependencies (not using default Starter), please refer to [Developer Guide - Dependency Extension Configuration](DEVELOPER_GUIDE-en.md#9-dependency-extension).

Start the project, click on Model Configuration, add a new model and fill in your API key.

![add-model.png](../img/add-model.png)

1. Standard Provider Integration: If you're using a built-in supported AI provider (like OpenAI, Deepseek, etc.), you usually only need to provide the Model Name and API Key.

2. Custom and Local Model Integration (Ollama/Self-hosted Gateway): This system is based on Spring AI architecture and supports the standard OpenAI interface protocol. If you're connecting to Ollama or other custom gateways, please note the following:

	- Protocol Compatibility: Please refer to the Spring AI official documentation about OpenAI compatibility to ensure your gateway response format meets the standard.

	- Address Configuration: For self-deployed models, please accurately fill in the base-url and completions-path. The system will concatenate them into the complete call address, for example: http://localhost:11434/v1/chat/completions

3. Troubleshooting: If the configuration doesn't work after setup, we recommend first using Postman to test your interface address to confirm network connectivity and parameter format are correct.


### 2.4 Embedding Model Batch Processing Strategy Configuration

> For detailed configuration parameters, see [Developer Guide - Embedding Batch Configuration](DEVELOPER_GUIDE-en.md#2-embedding-batch-configuration).

### 2.5 Vector Store Configuration

The system uses Chroma as its default persistent vector store and an embedded Lucene BM25 index for
hybrid retrieval. Start Chroma locally first:

```bash
docker compose -f docker-file/docker-compose.yml up -d chroma
```

Override the connection, tenant, database, and collection with `CHROMA_HOST`, `CHROMA_PORT`,
`CHROMA_TENANT`, `CHROMA_DATABASE`, and `CHROMA_COLLECTION`. For a temporary in-memory store,
set `SPRING_PROFILES_ACTIVE=simple`.

#### 2.5.1 Vector Store Dependency Import

You can import your preferred persistent vector store. You just need to provide a bean of type org.springframework.ai.vectorstore.VectorStore to the IoC container. For example, directly import the PGvector starter:

```xml
<dependency>
	<groupId>org.springframework.ai</groupId>
	<artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
```

For detailed vector store documentation, refer to: https://springdoc.cn/spring-ai/api/vectordbs.html

#### 2.5.2 Hybrid Retrieval Indexes

Chroma stores documents, metadata, and dense vectors. Lucene persists its BM25 index at
`KEYWORD_INDEX_PATH`. Writes update both indexes, and startup rebuilds Lucene from Chroma by default.
Docker Compose already defines the persistent `lucene-index` volume.

#### 2.5.3 Vector Store Configuration Parameters

> For detailed configuration parameters, see [Developer Guide - Vector Store Configuration](DEVELOPER_GUIDE-en.md#3-vector-store-configuration).

### 2.6 Retrieval Fusion Strategy

> For detailed configuration parameters, see [Developer Guide - Vector Store Configuration](DEVELOPER_GUIDE-en.md#3-vector-store-configuration).

### 2.7 Replace Vector Store Implementation

> To replace the default Chroma store (for example, with Simple, PGVector, or Milvus), see [Developer Guide - Vector Store Dependency Extension](DEVELOPER_GUIDE-en.md#vector-store-dependency-extension).
>
> The project ships `application-simple.yml` and `application-milvus.yml`. Select the matching profile to switch; see [Developer Guide - Ready-to-Use Configuration Examples](DEVELOPER_GUIDE-en.md#ready-to-use-configuration-examples).

### 2.8 Configure the Python Sandbox

DataAgent uses the Spring AI Alibaba `1.1.2.2` Sandbox to run generated Python code. Every
execution gets a task-scoped container; dynamic dependency installation, code execution, and
container cleanup all happen inside that task.

Confirm that Docker is available:

```bash
docker info
```

The default configuration uses local Docker discovery, public PyPI, and the AgentScope base
image. Development normally needs no override. To select a Docker endpoint, image, or private
package index, set:

```bash
export DATAAGENT_SANDBOX_DOCKER_HOST=unix:///var/run/docker.sock
export DATAAGENT_SANDBOX_IMAGE=agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/runtime-sandbox-base:latest
export DATAAGENT_PYPI_INDEX_URL=https://pypi.org/simple
```

Production should use a pinned image digest and an enterprise PyPI proxy, with infrastructure
network policy that allows the sandbox to reach only that proxy. See
[Advanced Features - Python Execution Environment Configuration](ADVANCED_FEATURES-en.md#python-execution-environment-configuration)
for the full configuration, dependency contract, and troubleshooting guidance.

## 3. Start Management Backend

Run the following command from the project root:

```bash
docker compose -f docker-file/docker-compose.yml up -d chroma
./mvnw -pl data-agent-management spring-boot:run
```

Or run `DataAgentApplication.java` directly in your IDE.

## 4. Start Web Frontend

Navigate to the `data-agent-frontend-nuxt` directory.

### 4.1 Install Dependencies

```bash
pnpm install
```

### 4.2 Start Service

```bash
pnpm dev
```

After successful startup, access http://localhost:3000

### 4.3 Verify Dynamic Python Dependencies

After configuring the agent, models, and data source, submit a request that explicitly includes a
Python step:

```text
Query the raw status and total_amount rows from orders, then aggregate them with Python.
Declare and import six==1.17.0 through PEP 723, and report the six version and status totals.
```

A successful timeline contains “Python Generation”, “Python Execution”, “Python Analysis”, and a
final report. The Python output should contain `six_version: 1.17.0`. No task container should
remain after completion:

```bash
docker ps --format '{{.Names}}' | grep '^dataagent-sandbox-'
```

No output means the task sandbox was removed. If dependency installation fails, the workflow
feeds the error into the next Python generation attempt. After the configured retry limit, it
enters the existing fallback or termination path.

## 5. System Experience

### 5.1 Creating and Configuring Data Agent

Visit http://localhost:3000 to see the current list of agents (there are four placeholder agents by default that are not connected to data; you can delete them and create new agents)

![homepage-agents.png](../img/homepage-agents.png)

Click "Create Agent" in the upper right corner. Here you only need to enter the agent name, and use default settings for other configurations.

![agent-create.png](../img/agent-create.png)

After creation, you can see the agent configuration page.

![agent-config.png](../img/agent-config.png)

#### Configure Data Source

Go to the data source configuration page and configure the business database (the business database we provided in the first step of environment initialization).

![datasource-config.png](../img/datasource-config.png)

After adding, you can verify the data source connection on the list page.

![datasource-validation.png](../img/datasource-validation.png)

For newly added data sources, you need to select which data tables to use for data analysis.

![datasource-tables.png](../img/datasource-tables.png)

Then click the "Initialize Data Source" button in the upper right corner.

![datasource-init.png](../img/datasource-init.png)

#### Configure Preset Questions

Preset question management allows you to set preset questions for the agent.

![preset-questions.png](../img/preset-questions.png)

#### Configure Semantic Model

Semantic model management allows you to set semantic models for the agent.
The semantic model library defines precise conversion rules from business terms to database physical structures, storing field name mappings.
For example, `customerSatisfactionScore` corresponds to the `csat_score` field in the database.

![semantic-models.png](../img/semantic-models.png)

#### Configure Business Knowledge

Business knowledge management allows you to set business knowledge for the agent.
Business knowledge defines business terms and business rules, such as GMV = Gross Merchandise Volume, including paid and unpaid order amounts.
Business knowledge can be set to recall or not recall. After configuration, click the "Sync to Vector Store" button in the upper right corner.

![business-knowledge.png](../img/business-knowledge.png)

After success, you can click "Go to Run Interface" to use the agent for data queries. After debugging is complete, you can publish the agent.

> Note: "Access API" is not fully implemented in the current version and is reserved for secondary development.

### 5.2 Running the Data Agent

Run Interface

![run-page.png](../img/run-page.png)

The left side of the run interface shows historical message records, and the right side shows current session records, input box, and request parameter configuration.

Enter a question in the input box and click the "Send" button to start querying.

![analyze-question.png](../img/analyze-question.png)

The analysis report is in HTML format. Click the "Download Report" button to download the final report.

![analyze-result.png](../img/analyze-result.png)

#### Run Modes

Besides the default request mode, the agent runtime also supports "Human Feedback", "NL2SQL Only", "Concise Report", and "Show SQL Results" modes.

**Default Mode**

By default, human feedback mode is not enabled. The agent automatically generates and executes the plan, parses SQL execution results, and generates a report.

**Human Feedback Mode**

If human feedback mode is enabled, the agent will wait for user confirmation after generating the plan, then modify or execute the plan based on the user's selected feedback result.

![feedback-mode.png](../img/feedback-mode.png)

**NL2SQL Only Mode**

"NL2SQL Only Mode" makes the agent only generate SQL and retrieve results without generating a report.

![nl2sql-mode.png](../img/nl2sql-mode.png)

**Show SQL Results**

"Show SQL Results" displays the SQL execution results to the user after generating SQL and retrieving results.

![show-sql-result.png](../img/show-sql-result.png)


## Next Steps

- Learn about [Architecture Design](ARCHITECTURE-en.md) to understand the system principles in depth
- Check [Advanced Features](ADVANCED_FEATURES-en.md) to learn about more advanced features
- Read [Developer Documentation](DEVELOPER_GUIDE-en.md) to contribute to the project
