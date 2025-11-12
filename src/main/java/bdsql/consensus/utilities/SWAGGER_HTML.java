package bdsql.consensus.utilities;

public class SWAGGER_HTML {
    public static final String HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>BDSQL Raft API Documentation</title>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.10.3/swagger-ui.min.css">
                <style>
                    body { margin: 0; padding: 0; }
                </style>
            </head>
            <body>
                <div id="swagger-ui"></div>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.10.3/swagger-ui-bundle.min.js"></script>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.10.3/swagger-ui-standalone-preset.min.js"></script>
                <script>
                    window.onload = function() {
                        const spec = {
                            "openapi": "3.0.0",
                            "info": {
                                "title": "BDSQL Raft Distributed Database API",
                                "version": "1.0.0",
                                "description": "REST API for interacting with a Raft-based distributed document database"
                            },
                            "servers": [
                                {
                                    "url": "http://localhost:8080",
                                    "description": "Local development server"
                                }
                            ],
                            "paths": {
                                "/admin": {
                                    "get": {
                                        "summary": "Admin Dashboard",
                                        "description": "HTML dashboard for monitoring cluster status",
                                        "tags": ["Admin"],
                                        "responses": {
                                            "200": {
                                                "description": "Returns HTML dashboard",
                                                "content": {
                                                    "text/html": {}
                                                }
                                            }
                                        }
                                    }
                                },
                                "/swagger": {
                                    "get": {
                                        "summary": "API Documentation",
                                        "description": "This Swagger UI page",
                                        "tags": ["Admin"],
                                        "responses": {
                                            "200": {
                                                "description": "Returns Swagger UI HTML",
                                                "content": {
                                                    "text/html": {}
                                                }
                                            }
                                        }
                                    }
                                },
                                "/api/status": {
                                    "get": {
                                        "summary": "Node Status",
                                        "description": "Get current node status including state, term, and indices",
                                        "tags": ["Cluster"],
                                        "responses": {
                                            "200": {
                                                "description": "Node status information",
                                                "content": {
                                                    "application/json": {
                                                        "schema": {
                                                            "type": "object",
                                                            "properties": {
                                                                "id": { "type": "string", "example": "node1" },
                                                                "state": { "type": "string", "enum": ["FOLLOWER", "CANDIDATE", "LEADER"] },
                                                                "term": { "type": "integer", "example": 5 },
                                                                "commitIndex": { "type": "integer", "example": 42 },
                                                                "lastApplied": { "type": "integer", "example": 42 }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                "/api/cluster": {
                                    "get": {
                                        "summary": "Cluster Information",
                                        "description": "Get information about cluster peers and replication status",
                                        "tags": ["Cluster"],
                                        "responses": {
                                            "200": {
                                                "description": "Cluster information",
                                                "content": {
                                                    "application/json": {
                                                        "schema": {
                                                            "type": "object",
                                                            "properties": {
                                                                "self": { "type": "string", "example": "node1" },
                                                                "peers": {
                                                                    "type": "array",
                                                                    "items": {
                                                                        "type": "object",
                                                                        "properties": {
                                                                            "addr": { "type": "string", "example": "localhost:5001" },
                                                                            "nextIndex": { "type": "integer", "example": 43 },
                                                                            "matchIndex": { "type": "integer", "example": 42 },
                                                                            "lastHeartbeat": { "type": "integer", "example": 1234567890 }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                "/api/log": {
                                    "get": {
                                        "summary": "Recent Log Entries",
                                        "description": "Get recent log entries (limited and truncated)",
                                        "tags": ["Log"],
                                        "parameters": [
                                            {
                                                "name": "count",
                                                "in": "query",
                                                "description": "Number of recent entries to return",
                                                "schema": {
                                                    "type": "integer",
                                                    "default": 50
                                                }
                                            }
                                        ],
                                        "responses": {
                                            "200": {
                                                "description": "Recent log entries",
                                                "content": {
                                                    "application/json": {
                                                        "schema": {
                                                            "type": "object",
                                                            "properties": {
                                                                "entries": {
                                                                    "type": "array",
                                                                    "items": {
                                                                        "type": "object",
                                                                        "properties": {
                                                                            "index": { "type": "integer" },
                                                                            "term": { "type": "integer" },
                                                                            "data": { "type": "string", "description": "Truncated to 200 chars" }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                "/api/log/full": {
                                    "get": {
                                        "summary": "Full Log Entries",
                                        "description": "Get all log entries (complete, not truncated)",
                                        "tags": ["Log"],
                                        "responses": {
                                            "200": {
                                                "description": "All log entries",
                                                "content": {
                                                    "application/json": {
                                                        "schema": {
                                                            "type": "object",
                                                            "properties": {
                                                                "totalEntries": { "type": "integer", "example": 150 },
                                                                "entries": {
                                                                    "type": "array",
                                                                    "items": {
                                                                        "type": "object",
                                                                        "properties": {
                                                                            "index": { "type": "integer" },
                                                                            "term": { "type": "integer" },
                                                                            "data": { "type": "string", "description": "Full data content" }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                "/api/doc": {
                                    "get": {
                                        "summary": "Query Documents",
                                        "description": "Query documents from a collection",
                                        "tags": ["Documents"],
                                        "parameters": [
                                            {
                                                "name": "collection",
                                                "in": "query",
                                                "required": true,
                                                "description": "Collection name",
                                                "schema": { "type": "string", "example": "users" }
                                            },
                                            {
                                                "name": "id",
                                                "in": "query",
                                                "description": "Document ID (optional, for single document retrieval)",
                                                "schema": { "type": "string" }
                                            },
                                            {
                                                "name": "field",
                                                "in": "query",
                                                "description": "Field name for simple query (requires 'value')",
                                                "schema": { "type": "string" }
                                            },
                                            {
                                                "name": "value",
                                                "in": "query",
                                                "description": "Field value for simple query (requires 'field')",
                                                "schema": { "type": "string" }
                                            }
                                        ],
                                        "requestBody": {
                                            "description": "Complex query as JSON (optional)",
                                            "content": {
                                                "application/json": {
                                                    "schema": {
                                                        "type": "object",
                                                        "additionalProperties": true,
                                                        "example": { "status": "active", "age": 25 }
                                                    }
                                                }
                                            }
                                        },
                                        "responses": {
                                            "200": {
                                                "description": "Document(s) found",
                                                "content": {
                                                    "application/json": {
                                                        "schema": {
                                                            "oneOf": [
                                                                {
                                                                    "type": "object",
                                                                    "description": "Single document (when id is specified)"
                                                                },
                                                                {
                                                                    "type": "array",
                                                                    "description": "Array of documents (when querying)",
                                                                    "items": { "type": "object" }
                                                                }
                                                            ]
                                                        }
                                                    }
                                                }
                                            },
                                            "400": {
                                                "description": "Bad request",
                                                "content": {
                                                    "application/json": {
                                                        "schema": {
                                                            "type": "object",
                                                            "properties": {
                                                                "error": { "type": "string" }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            "404": {
                                                "description": "Document not found"
                                            }
                                        }
                                    },
                                    "post": {
                                        "summary": "Insert Document",
                                        "description": "Insert a new document into a collection",
                                        "tags": ["Documents"],
                                        "parameters": [
                                            {
                                                "name": "collection",
                                                "in": "query",
                                                "description": "Collection name (optional if included in body)",
                                                "schema": { "type": "string" }
                                            }
                                        ],
                                        "requestBody": {
                                            "required": true,
                                            "content": {
                                                "application/json": {
                                                    "schema": {
                                                        "oneOf": [
                                                            {
                                                                "type": "object",
                                                                "description": "Document only (when collection is in query param)",
                                                                "example": { "name": "John", "age": 30 }
                                                            },
                                                            {
                                                                "type": "object",
                                                                "description": "Full format with collection and document",
                                                                "properties": {
                                                                    "collection": { "type": "string" },
                                                                    "document": { "type": "object" }
                                                                },
                                                                "required": ["collection", "document"],
                                                                "example": {
                                                                    "collection": "users",
                                                                    "document": { "name": "John", "age": 30 }
                                                                }
                                                            }
                                                        ]
                                                    }
                                                }
                                            }
                                        },
                                        "responses": {
                                            "200": {
                                                "description": "Document inserted successfully",
                                                "content": {
                                                    "application/json": {
                                                        "schema": {
                                                            "type": "object",
                                                            "properties": {
                                                                "index": { "type": "integer", "description": "Log index of the operation" }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            "307": {
                                                "description": "Temporary redirect to leader node",
                                                "headers": {
                                                    "Location": {
                                                        "schema": { "type": "string" },
                                                        "description": "URL of the leader node"
                                                    }
                                                }
                                            },
                                            "400": {
                                                "description": "Bad request"
                                            },
                                            "500": {
                                                "description": "Not leader or operation failed"
                                            }
                                        }
                                    },
                                    "put": {
                                        "summary": "Update Document",
                                        "description": "Update an existing document",
                                        "tags": ["Documents"],
                                        "parameters": [
                                            {
                                                "name": "collection",
                                                "in": "query",
                                                "description": "Collection name",
                                                "schema": { "type": "string" }
                                            },
                                            {
                                                "name": "id",
                                                "in": "query",
                                                "description": "Document ID",
                                                "schema": { "type": "string" }
                                            }
                                        ],
                                        "requestBody": {
                                            "required": true,
                                            "content": {
                                                "application/json": {
                                                    "schema": {
                                                        "oneOf": [
                                                            {
                                                                "type": "object",
                                                                "description": "Updates only (when collection and id are in query params)",
                                                                "example": { "age": 31, "status": "active" }
                                                            },
                                                            {
                                                                "type": "object",
                                                                "description": "Full format",
                                                                "properties": {
                                                                    "collection": { "type": "string" },
                                                                    "id": { "type": "string" },
                                                                    "updates": { "type": "object" }
                                                                },
                                                                "example": {
                                                                    "collection": "users",
                                                                    "id": "user_123",
                                                                    "updates": { "age": 31 }
                                                                }
                                                            }
                                                        ]
                                                    }
                                                }
                                            }
                                        },
                                        "responses": {
                                            "200": {
                                                "description": "Document updated successfully",
                                                "content": {
                                                    "application/json": {
                                                        "schema": {
                                                            "type": "object",
                                                            "properties": {
                                                                "index": { "type": "integer" }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            "307": {
                                                "description": "Redirect to leader"
                                            },
                                            "400": {
                                                "description": "Bad request"
                                            },
                                            "500": {
                                                "description": "Not leader or operation failed"
                                            }
                                        }
                                    },
                                    "delete": {
                                        "summary": "Delete Document",
                                        "description": "Delete a document from a collection",
                                        "tags": ["Documents"],
                                        "parameters": [
                                            {
                                                "name": "collection",
                                                "in": "query",
                                                "required": true,
                                                "schema": { "type": "string" }
                                            },
                                            {
                                                "name": "id",
                                                "in": "query",
                                                "required": true,
                                                "schema": { "type": "string" }
                                            }
                                        ],
                                        "requestBody": {
                                            "description": "Optional body format",
                                            "content": {
                                                "application/json": {
                                                    "schema": {
                                                        "type": "object",
                                                        "properties": {
                                                            "collection": { "type": "string" },
                                                            "id": { "type": "string" }
                                                        },
                                                        "example": {
                                                            "collection": "users",
                                                            "id": "user_123"
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        "responses": {
                                            "200": {
                                                "description": "Document deleted successfully",
                                                "content": {
                                                    "application/json": {
                                                        "schema": {
                                                            "type": "object",
                                                            "properties": {
                                                                "index": { "type": "integer" }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            "307": {
                                                "description": "Redirect to leader"
                                            },
                                            "400": {
                                                "description": "Bad request"
                                            },
                                            "500": {
                                                "description": "Not leader or operation failed"
                                            }
                                        }
                                    }
                                },
                                "/api/doc/index": {
                                    "post": {
                                        "summary": "Create Index",
                                        "description": "Create a secondary index on a field in a collection",
                                        "tags": ["Documents"],
                                        "parameters": [
                                            {
                                                "name": "collection",
                                                "in": "query",
                                                "description": "Collection name",
                                                "schema": { "type": "string" }
                                            },
                                            {
                                                "name": "field",
                                                "in": "query",
                                                "description": "Field name to index",
                                                "schema": { "type": "string" }
                                            }
                                        ],
                                        "requestBody": {
                                            "description": "Alternative body format",
                                            "content": {
                                                "application/json": {
                                                    "schema": {
                                                        "type": "object",
                                                        "properties": {
                                                            "collection": { "type": "string" },
                                                            "field": { "type": "string" }
                                                        },
                                                        "required": ["collection", "field"],
                                                        "example": {
                                                            "collection": "users",
                                                            "field": "email"
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        "responses": {
                                            "200": {
                                                "description": "Index created successfully",
                                                "content": {
                                                    "application/json": {
                                                        "schema": {
                                                            "type": "object",
                                                            "properties": {
                                                                "index": { "type": "integer" }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            "307": {
                                                "description": "Redirect to leader"
                                            },
                                            "400": {
                                                "description": "Bad request"
                                            },
                                            "405": {
                                                "description": "Method not allowed"
                                            },
                                            "500": {
                                                "description": "Not leader or operation failed"
                                            }
                                        }
                                    }
                                }
                            },
                            "tags": [
                                { "name": "Admin", "description": "Administrative interfaces" },
                                { "name": "Cluster", "description": "Cluster status and information" },
                                { "name": "Log", "description": "Raft log inspection" },
                                { "name": "Documents", "description": "Document CRUD operations" }
                            ]
                        };

                        SwaggerUIBundle({
                            spec: spec,
                            dom_id: '#swagger-ui',
                            deepLinking: true,
                            presets: [
                                SwaggerUIBundle.presets.apis,
                                SwaggerUIStandalonePreset
                            ],
                            plugins: [
                                SwaggerUIBundle.plugins.DownloadUrl
                            ],
                            layout: "StandaloneLayout"
                        });
                    };
                </script>
            </body>
            </html>
            """;
}
