# BDSQL: A Replicated Document Store

BDSQL is a fault-tolerant, distributed NoSQL document database built in Java.

It uses the **Raft consensus algorithm** to replicate a JSON document store, ensuring that data remains consistent and available even if some nodes in the cluster fail. Communication between nodes for consensus is handled by **gRPC**, and the client-facing API is served over **HTTP**.

The underlying storage engine is **MapDB**, which provides high-performance, on-disk, transactional B-Trees and HashMaps.

## 🚀 Features

  * **Distributed Consensus:** Built on the Raft algorithm for leader election and replicated logging.
  * **Fault-Tolerant:** Can withstand the failure of (N-1)/2 nodes in an N-node cluster.
  * **JSON Document API:** A simple, MongoDB-like interface for storing and querying JSON documents.
  * **CRUD Operations:** Full support for Create, Read, Update, and Delete operations.
  * **Secondary Indexes:** Ability to create B-Tree indexes on document fields for fast queries.
  * **HTTP/JSON Client API:** All database operations are exposed via a simple REST-like HTTP API.
  * **Admin Dashboard:** A basic web UI to inspect the state of the cluster.

-----

## 🏗️ Architecture

The system is composed of several key components that run within each `RaftNode`:

1.  **gRPC Server (Internal):** Used for all node-to-node communication. This is the "Raft" layer, handling `RequestVote` and `AppendEntries` messages to manage consensus.
2.  **HTTP Server (External):** This is the client-facing API. All database commands (like `insert`, `find`) are sent to this server. It runs on `port + 1000` (relative to the gRPC port).
3.  **Raft Log (WAL):** When a client sends a write request (insert, update, delete) to the leader's HTTP server, the leader **does not** execute it immediately. Instead, it creates a "command" (`cmd`) object and writes it to its Write-Ahead Log.
4.  **Replication:** The leader uses gRPC to replicate this `cmd` to all follower nodes.
5.  **State Machine (BTreeDocumentStore):** Once a `cmd` is committed (saved by a majority of nodes), the log manager on *every node* passes it to the `createDocumentApplyFunction`. This function parses the `cmd` and finally executes the operation (e.g., `documentStore.insert(...)`) on its local MapDB database.

This "replicated log" architecture ensures that every node executes the exact same commands in the exact same order, resulting in an identical, consistent copy of the data across the cluster.

-----

## 📦 How to Build

The project appears to be built with Maven. You can build the all-in-one "fat JAR" by running:

```bash
mvn clean package
```

This will create the runnable JAR in `target/bdsql-1.0-SNAPSHOT.jar`.

-----

## 🏃‍♂️ How to Run a Cluster

To run the database, you must start at least 3 nodes to form a fault-tolerant cluster. Each node must be aware of all other nodes in the cluster at startup.

You will need to open a separate terminal for each node.

### Example: 3-Node Local Cluster

**Terminal 1 (Node 1):**

  * **Raft (gRPC) Port:** `50051`
  * **Client (HTTP) Port:** `60051`

<!-- end list -->

```bash
java -cp "target/bdsql-1.0-SNAPSHOT.jar" bdsql.Main node1 127.0.0.1:50051 127.0.0.1:50051 127.0.0.1:50052 127.0.0.1:50053
```

**Terminal 2 (Node 2):**

  * **Raft (gRPC) Port:** `50052`
  * **Client (HTTP) Port:** `60052`

<!-- end list -->

```bash
java -cp "target/bdsql-1.0-SNAPSHOT.jar" bdsql.Main node2 127.0.0.1:50052 127.0.0.1:50051 127.0.0.1:50052 127.0.0.1:50053
```

**Terminal 3 (Node 3):**

  * **Raft (gRPC) Port:** `50053`
  * **Client (HTTP) Port:** `60053`

<!-- end list -->

```bash
java -cp "target/bdsql-1.0-SNAPSHOT.jar" bdsql.Main node3 127.0.0.1:50053 127.0.0.1:50051 127.0.0.1:50052 127.0.0.1:50053
```

The cluster will take a few seconds to elect a leader. You can interact with the Client (HTTP) API on any node. If you send a write request to a follower, it will automatically redirect you to the leader.

-----

## 💻 API Usage (with `curl`)

You can use the HTTP API (ports `60051`, `60052`, etc.) to interact with the database.

### 1\. Insert a Document

This inserts a document into the `users` collection. The database will automatically add `_id`, `_createdAt`, and `_updatedAt` fields.

```bash
curl -X POST 'http://127.0.0.1:60051/api/doc?collection=users' \
     -H 'Content-Type: application/json' \
     -d '{"username": "jdoe", "email": "jdoe@example.com"}'
```

### 2\. Find a Document (by ID)

Let's assume the previous insert created an ID `users_12345_1`.

```bash
curl -X GET 'http://127.0.0.1:60051/api/doc?collection=users&id=users_12345_1'
```

### 3\. Update a Document

This updates the document by merging the new JSON.

```bash
curl -X PUT 'http://127.0.0.1:60051/api/doc?collection=users&id=users_12345_1' \
     -H 'Content-Type: application/json' \
     -d '{"location": "New York"}'
```

### 4\. Create an Index

This creates a secondary index on the `username` field for faster lookups. This is a write operation and will be replicated.

```bash
curl -X POST 'http://127.0.0.1:60051/api/doc/index?collection=users&field=username'
```

### 5\. Find by Indexed Field

Now you can efficiently query by the `username` field.

```bash
curl -X GET 'http://127.0.0.1:60051/api/doc?collection=users&field=username&value=jdoe'
```

**Response:**

```json
[
  {
    "username": "jdoe",
    "email": "jdoe@example.com",
    "location": "New York",
    "_id": "users_12345_1",
    "_createdAt": 1678886400000,
    "_updatedAt": 1678886401000
  }
]
```

### 6\. Delete a Document

```bash
curl -X DELETE 'http://127.0.0.1:60051/api/doc?collection=users&id=users_12345_1'
```

### 7\. View Admin Dashboard

You can see the status of any node (its ID, state, term, and peer info) by opening its admin URL in a browser:

**`http://127.0.0.1:60051/admin`**
