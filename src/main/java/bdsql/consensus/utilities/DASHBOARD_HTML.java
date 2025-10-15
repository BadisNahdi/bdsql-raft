package bdsql.consensus.utilities;

public class DASHBOARD_HTML {
    public static final String HTML =
"<!doctype html>\n" +
"<html>\n" +
"<head>\n" +
"  <meta charset=\"utf-8\">\n" +
"  <title>bdSQL Raft Cluster Dashboard</title>\n" +
"  <style>\n" +
"    * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
"    body {\n" +
"      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;\n" +
"      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
"      min-height: 100vh;\n" +
"      padding: 20px;\n" +
"      color: #2d3748;\n" +
"    }\n" +
"    .container { max-width: 1400px; margin: 0 auto; }\n" +
"    .header {\n" +
"      background: white;\n" +
"      border-radius: 16px;\n" +
"      padding: 24px;\n" +
"      margin-bottom: 20px;\n" +
"      box-shadow: 0 10px 40px rgba(0,0,0,0.1);\n" +
"    }\n" +
"    .header h1 {\n" +
"      font-size: 28px;\n" +
"      font-weight: 700;\n" +
"      color: #1a202c;\n" +
"      margin-bottom: 12px;\n" +
"    }\n" +
"    .status-bar {\n" +
"      display: flex;\n" +
"      gap: 20px;\n" +
"      flex-wrap: wrap;\n" +
"    }\n" +
"    .status-item {\n" +
"      display: flex;\n" +
"      align-items: center;\n" +
"      gap: 8px;\n" +
"    }\n" +
"    .status-label {\n" +
"      font-size: 13px;\n" +
"      color: #718096;\n" +
"      font-weight: 500;\n" +
"    }\n" +
"    .status-value {\n" +
"      font-size: 15px;\n" +
"      font-weight: 600;\n" +
"      color: #2d3748;\n" +
"    }\n" +
"    .badge {\n" +
"      display: inline-block;\n" +
"      padding: 4px 12px;\n" +
"      border-radius: 12px;\n" +
"      font-size: 12px;\n" +
"      font-weight: 600;\n" +
"      text-transform: uppercase;\n" +
"    }\n" +
"    .badge.leader { background: #48bb78; color: white; }\n" +
"    .badge.follower { background: #4299e1; color: white; }\n" +
"    .badge.candidate { background: #ed8936; color: white; }\n" +
"    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px; }\n" +
"    @media (max-width: 1024px) { .grid { grid-template-columns: 1fr; } }\n" +
"    .card {\n" +
"      background: white;\n" +
"      border-radius: 16px;\n" +
"      padding: 24px;\n" +
"      box-shadow: 0 10px 40px rgba(0,0,0,0.1);\n" +
"    }\n" +
"    .card h3 {\n" +
"      font-size: 18px;\n" +
"      font-weight: 600;\n" +
"      margin-bottom: 16px;\n" +
"      color: #1a202c;\n" +
"    }\n" +
"    #network-viz {\n" +
"      position: relative;\n" +
"      height: 400px;\n" +
"      background: linear-gradient(135deg, #f7fafc 0%, #edf2f7 100%);\n" +
"      border-radius: 12px;\n" +
"      overflow: hidden;\n" +
"    }\n" +
"    .node {\n" +
"      position: absolute;\n" +
"      width: 140px;\n" +
"      height: 140px;\n" +
"      transform: translate(-50%, -50%);\n" +
"      cursor: pointer;\n" +
"      transition: transform 0.2s;\n" +
"    }\n" +
"    .node:hover { transform: translate(-50%, -50%) scale(1.1); }\n" +
"    .node-circle {\n" +
"      position: absolute;\n" +
"      width: 90px;\n" +
"      height: 90px;\n" +
"      top: 50%;\n" +
"      left: 50%;\n" +
"      transform: translate(-50%, -50%);\n" +
"      border-radius: 50%;\n" +
"      background: white;\n" +
"      box-shadow: 0 8px 24px rgba(0,0,0,0.12);\n" +
"      display: flex;\n" +
"      flex-direction: column;\n" +
"      align-items: center;\n" +
"      justify-content: center;\n" +
"      border: 3px solid #e2e8f0;\n" +
"    }\n" +
"    .node-circle.leader { border-color: #48bb78; background: linear-gradient(135deg, #48bb78 0%, #38a169 100%); color: white; }\n" +
"    .node-circle.follower { border-color: #4299e1; }\n" +
"    .node-circle.candidate { border-color: #ed8936; }\n" +
"    .node-circle.disconnected { border-color: #fc8181; opacity: 0.6; }\n" +
"    .node-id {\n" +
"      font-size: 13px;\n" +
"      font-weight: 700;\n" +
"      margin-bottom: 2px;\n" +
"      text-align: center;\n" +
"      word-break: break-all;\n" +
"    }\n" +
"    .node-role {\n" +
"      font-size: 10px;\n" +
"      text-transform: uppercase;\n" +
"      font-weight: 600;\n" +
"      opacity: 0.8;\n" +
"    }\n" +
"    .heartbeat {\n" +
"      position: absolute;\n" +
"      width: 100%;\n" +
"      height: 100%;\n" +
"      top: 0;\n" +
"      left: 0;\n" +
"      border-radius: 50%;\n" +
"      border: 2px solid;\n" +
"      pointer-events: none;\n" +
"      opacity: 0;\n" +
"    }\n" +
"    .heartbeat.leader { border-color: #48bb78; }\n" +
"    .heartbeat.follower { border-color: #4299e1; }\n" +
"    .heartbeat.candidate { border-color: #ed8936; }\n" +
"    .heartbeat.active {\n" +
"      animation: pulse 1s ease-out;\n" +
"    }\n" +
"    @keyframes pulse {\n" +
"      0% { transform: scale(0.8); opacity: 1; }\n" +
"      100% { transform: scale(1.4); opacity: 0; }\n" +
"    }\n" +
"    .connection-line {\n" +
"      position: absolute;\n" +
"      height: 2px;\n" +
"      background: rgba(66, 153, 225, 0.3);\n" +
"      transform-origin: left center;\n" +
"      pointer-events: none;\n" +
"      transition: background 0.3s;\n" +
"    }\n" +
"    .connection-line.active { background: rgba(72, 187, 120, 0.6); }\n" +
"    .peer-list {\n" +
"      display: flex;\n" +
"      flex-direction: column;\n" +
"      gap: 12px;\n" +
"    }\n" +
"    .peer-item {\n" +
"      display: flex;\n" +
"      align-items: center;\n" +
"      justify-content: space-between;\n" +
"      padding: 12px 16px;\n" +
"      background: #f7fafc;\n" +
"      border-radius: 8px;\n" +
"      border-left: 4px solid #4299e1;\n" +
"      transition: all 0.3s;\n" +
"    }\n" +
"    .peer-item.disconnected {\n" +
"      border-left-color: #fc8181;\n" +
"      opacity: 0.6;\n" +
"    }\n" +
"    .peer-info { display: flex; align-items: center; gap: 12px; }\n" +
"    .peer-indicator {\n" +
"      width: 12px;\n" +
"      height: 12px;\n" +
"      border-radius: 50%;\n" +
"      background: #48bb78;\n" +
"      transition: all 0.2s;\n" +
"    }\n" +
"    .peer-indicator.active {\n" +
"      animation: heartbeat-pulse 0.5s ease-out;\n" +
"    }\n" +
"    .peer-indicator.disconnected {\n" +
"      background: #fc8181;\n" +
"      animation: none;\n" +
"    }\n" +
"    @keyframes heartbeat-pulse {\n" +
"      0% { transform: scale(1); }\n" +
"      50% { transform: scale(1.3); box-shadow: 0 0 10px rgba(72, 187, 120, 0.6); }\n" +
"      100% { transform: scale(1); }\n" +
"    }\n" +
"    .peer-addr { font-size: 14px; font-weight: 600; color: #2d3748; }\n" +
"    .peer-stats {\n" +
"      display: flex;\n" +
"      gap: 16px;\n" +
"      font-size: 12px;\n" +
"      color: #718096;\n" +
"    }\n" +
"    .peer-heartbeat-time {\n" +
"      font-size: 11px;\n" +
"      color: #a0aec0;\n" +
"      margin-top: 4px;\n" +
"    }\n" +
"    .log-table {\n" +
"      width: 100%;\n" +
"      border-collapse: separate;\n" +
"      border-spacing: 0;\n" +
"    }\n" +
"    .log-table thead {\n" +
"      background: #f7fafc;\n" +
"      position: sticky;\n" +
"      top: 0;\n" +
"    }\n" +
"    .log-table th {\n" +
"      padding: 12px;\n" +
"      text-align: left;\n" +
"      font-size: 12px;\n" +
"      font-weight: 600;\n" +
"      color: #4a5568;\n" +
"      text-transform: uppercase;\n" +
"      border-bottom: 2px solid #e2e8f0;\n" +
"    }\n" +
"    .log-table td {\n" +
"      padding: 12px;\n" +
"      border-bottom: 1px solid #e2e8f0;\n" +
"      font-size: 13px;\n" +
"    }\n" +
"    .log-table tbody tr:hover { background: #f7fafc; }\n" +
"    .log-controls {\n" +
"      display: flex;\n" +
"      gap: 8px;\n" +
"      margin-bottom: 16px;\n" +
"      align-items: center;\n" +
"    }\n" +
"    input, button, select {\n" +
"      padding: 8px 16px;\n" +
"      border: 2px solid #e2e8f0;\n" +
"      border-radius: 8px;\n" +
"      font-size: 14px;\n" +
"      outline: none;\n" +
"      transition: all 0.2s;\n" +
"    }\n" +
"    input:focus, select:focus { border-color: #4299e1; }\n" +
"    button {\n" +
"      background: #4299e1;\n" +
"      color: white;\n" +
"      font-weight: 600;\n" +
"      border: none;\n" +
"      cursor: pointer;\n" +
"    }\n" +
"    button:hover { background: #3182ce; transform: translateY(-1px); }\n" +
"    button:active { transform: translateY(0); }\n" +
"    button.success { background: #48bb78; }\n" +
"    button.success:hover { background: #38a169; }\n" +
"    button.danger { background: #f56565; }\n" +
"    button.danger:hover { background: #e53e3e; }\n" +
"    .doc-form {\n" +
"      display: flex;\n" +
"      flex-direction: column;\n" +
"      gap: 12px;\n" +
"      margin-bottom: 16px;\n" +
"    }\n" +
"    .form-row {\n" +
"      display: flex;\n" +
"      gap: 8px;\n" +
"      flex-wrap: wrap;\n" +
"    }\n" +
"    .form-row input, .form-row select { flex: 1; min-width: 150px; }\n" +
"    textarea {\n" +
"      padding: 12px;\n" +
"      border: 2px solid #e2e8f0;\n" +
"      border-radius: 8px;\n" +
"      font-size: 14px;\n" +
"      font-family: monospace;\n" +
"      resize: vertical;\n" +
"      min-height: 100px;\n" +
"      outline: none;\n" +
"      transition: all 0.2s;\n" +
"    }\n" +
"    textarea:focus { border-color: #4299e1; }\n" +
"    #clientresp {\n" +
"      margin-top: 12px;\n" +
"      padding: 12px;\n" +
"      background: #f7fafc;\n" +
"      border-radius: 8px;\n" +
"      font-family: monospace;\n" +
"      font-size: 12px;\n" +
"      color: #2d3748;\n" +
"      max-height: 200px;\n" +
"      overflow: auto;\n" +
"      white-space: pre-wrap;\n" +
"      word-wrap: break-word;\n" +
"    }\n" +
"    .collection-badge {\n" +
"      display: inline-block;\n" +
"      padding: 4px 10px;\n" +
"      margin: 4px;\n" +
"      background: #4299e1;\n" +
"      color: white;\n" +
"      border-radius: 6px;\n" +
"      font-size: 12px;\n" +
"      font-weight: 600;\n" +
"    }\n" +
"    .collection-count {\n" +
"      background: rgba(255,255,255,0.3);\n" +
"      padding: 2px 6px;\n" +
"      border-radius: 4px;\n" +
"      margin-left: 4px;\n" +
"    }\n" +
"    .tabs {\n" +
"      display: flex;\n" +
"      gap: 8px;\n" +
"      margin-bottom: 16px;\n" +
"      border-bottom: 2px solid #e2e8f0;\n" +
"    }\n" +
"    .tab {\n" +
"      padding: 10px 20px;\n" +
"      background: none;\n" +
"      border: none;\n" +
"      border-bottom: 3px solid transparent;\n" +
"      cursor: pointer;\n" +
"      font-weight: 600;\n" +
"      color: #718096;\n" +
"      transition: all 0.2s;\n" +
"    }\n" +
"    .tab:hover { color: #4299e1; transform: none; }\n" +
"    .tab.active {\n" +
"      color: #4299e1;\n" +
"      border-bottom-color: #4299e1;\n" +
"    }\n" +
"    .tab-content { display: none; }\n" +
"    .tab-content.active { display: block; }\n" +
"  </style>\n" +
"</head>\n" +
"<body>\n" +
"  <div class=\"container\">\n" +
"    <div class=\"header\">\n" +
"      <h1>🚀 bdSQL Raft Cluster Dashboard</h1>\n" +
"      <div class=\"status-bar\">\n" +
"        <div class=\"status-item\">\n" +
"          <span class=\"status-label\">Node ID:</span>\n" +
"          <span class=\"status-value\" id=\"node-id\">-</span>\n" +
"        </div>\n" +
"        <div class=\"status-item\">\n" +
"          <span class=\"status-label\">State:</span>\n" +
"          <span id=\"node-state\">-</span>\n" +
"        </div>\n" +
"        <div class=\"status-item\">\n" +
"          <span class=\"status-label\">Term:</span>\n" +
"          <span class=\"status-value\" id=\"node-term\">-</span>\n" +
"        </div>\n" +
"        <div class=\"status-item\">\n" +
"          <span class=\"status-label\">Commit Index:</span>\n" +
"          <span class=\"status-value\" id=\"commit-index\">-</span>\n" +
"        </div>\n" +
"        <div class=\"status-item\">\n" +
"          <span class=\"status-label\">Last Applied:</span>\n" +
"          <span class=\"status-value\" id=\"last-applied\">-</span>\n" +
"        </div>\n" +
"      </div>\n" +
"    </div>\n" +
"\n" +
"    <div class=\"grid\">\n" +
"      <div class=\"card\">\n" +
"        <h3>Network Topology</h3>\n" +
"        <div id=\"network-viz\"></div>\n" +
"      </div>\n" +
"\n" +
"      <div class=\"card\">\n" +
"        <h3>Cluster Peers</h3>\n" +
"        <div id=\"peer-list\" class=\"peer-list\"></div>\n" +
"      </div>\n" +
"    </div>\n" +
"\n" +
"    <div class=\"card\" style=\"margin-bottom: 20px;\">\n" +
"      <h3>📦 Collections</h3>\n" +
"      <div id=\"collections-list\" style=\"margin-bottom: 12px;\"></div>\n" +
"      <button id=\"refresh-collections\" class=\"success\">Refresh Collections</button>\n" +
"    </div>\n" +
"\n" +
"    <div class=\"card\" style=\"margin-bottom: 20px;\">\n" +
"      <h3>Replication Log</h3>\n" +
"      <div class=\"log-controls\">\n" +
"        <label style=\"font-size: 14px; color: #4a5568;\">Show last:</label>\n" +
"        <input id=\"count\" type=\"number\" value=\"50\" style=\"width: 80px;\">\n" +
"        <button id=\"refresh\">Refresh</button>\n" +
"      </div>\n" +
"      <div style=\"max-height: 400px; overflow-y: auto;\">\n" +
"        <table class=\"log-table\">\n" +
"          <thead>\n" +
"            <tr>\n" +
"              <th>Index</th>\n" +
"              <th>Term</th>\n" +
"              <th>Data</th>\n" +
"            </tr>\n" +
"          </thead>\n" +
"          <tbody id=\"log-tbody\"></tbody>\n" +
"        </table>\n" +
"      </div>\n" +
"    </div>\n" +
"\n" +
"    <div class=\"card\">\n" +
"      <h3>📄 Document Operations</h3>\n" +
"      <div class=\"tabs\">\n" +
"        <button class=\"tab active\" data-tab=\"insert\">Insert</button>\n" +
"        <button class=\"tab\" data-tab=\"query\">Query</button>\n" +
"        <button class=\"tab\" data-tab=\"update\">Update</button>\n" +
"        <button class=\"tab\" data-tab=\"delete\">Delete</button>\n" +
"        <button class=\"tab\" data-tab=\"index\">Create Index</button>\n" +
"      </div>\n" +
"\n" +
"      <div class=\"tab-content active\" id=\"insert-tab\">\n" +
"        <form id=\"insertform\" class=\"doc-form\">\n" +
"          <div class=\"form-row\">\n" +
"            <input id=\"insert-collection\" placeholder=\"Collection\" required>\n" +
"          </div>\n" +
"          <textarea id=\"insert-document\" placeholder='{\"name\": \"John\", \"email\": \"john@example.com\"}' required></textarea>\n" +
"          <button type=\"submit\" class=\"success\">Insert Document</button>\n" +
"        </form>\n" +
"      </div>\n" +
"\n" +
"      <div class=\"tab-content\" id=\"query-tab\">\n" +
"        <form id=\"queryform\" class=\"doc-form\">\n" +
"          <div class=\"form-row\">\n" +
"            <input id=\"query-collection\" placeholder=\"Collection\" required>\n" +
"            <input id=\"query-limit\" type=\"number\" placeholder=\"Limit (default 100)\" value=\"100\">\n" +
"          </div>\n" +
"          <textarea id=\"query-conditions\" placeholder='{\"status\": \"active\", \"age\": 30}'></textarea>\n" +
"          <div class=\"form-row\">\n" +
"            <button type=\"submit\">Query with Conditions</button>\n" +
"            <button type=\"button\" id=\"query-all-btn\" class=\"success\">Get All Documents</button>\n" +
"          </div>\n" +
"        </form>\n" +
"      </div>\n" +
"\n" +
"      <div class=\"tab-content\" id=\"update-tab\">\n" +
"        <form id=\"updateform\" class=\"doc-form\">\n" +
"          <div class=\"form-row\">\n" +
"            <input id=\"update-collection\" placeholder=\"Collection\" required>\n" +
"            <input id=\"update-id\" placeholder=\"Document ID\" required>\n" +
"          </div>\n" +
"          <textarea id=\"update-data\" placeholder='{\"age\": 31, \"status\": \"inactive\"}' required></textarea>\n" +
"          <button type=\"submit\">Update Document</button>\n" +
"        </form>\n" +
"      </div>\n" +
"\n" +
"      <div class=\"tab-content\" id=\"delete-tab\">\n" +
"        <form id=\"deleteform\" class=\"doc-form\">\n" +
"          <div class=\"form-row\">\n" +
"            <input id=\"delete-collection\" placeholder=\"Collection\" required>\n" +
"            <input id=\"delete-id\" placeholder=\"Document ID\" required>\n" +
"          </div>\n" +
"          <button type=\"submit\" class=\"danger\">Delete Document</button>\n" +
"        </form>\n" +
"      </div>\n" +
"\n" +
"      <div class=\"tab-content\" id=\"index-tab\">\n" +
"        <form id=\"indexform\" class=\"doc-form\">\n" +
"          <div class=\"form-row\">\n" +
"            <input id=\"index-collection\" placeholder=\"Collection\" required>\n" +
"            <input id=\"index-field\" placeholder=\"Field name (e.g., email)\" required>\n" +
"          </div>\n" +
"          <button type=\"submit\" class=\"success\">Create Index</button>\n" +
"        </form>\n" +
"      </div>\n" +
"\n" +
"      <div id=\"clientresp\"></div>\n" +
"    </div>\n" +
"  </div>\n" +
"\n" +
"  <script>\n" +
"    let nodeData = { self: '', peers: [], state: 'FOLLOWER' };\n" +
"    let lastHeartbeats = {};\n" +
"    const HEARTBEAT_TIMEOUT = 2000;\n" +
"\n" +
"    // Tab switching\n" +
"    document.querySelectorAll('.tab').forEach(tab => {\n" +
"      tab.addEventListener('click', () => {\n" +
"        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));\n" +
"        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));\n" +
"        tab.classList.add('active');\n" +
"        document.getElementById(tab.dataset.tab + '-tab').classList.add('active');\n" +
"      });\n" +
"    });\n" +
"\n" +
"    async function fetchJson(path) {\n" +
"      try {\n" +
"        const r = await fetch(path);\n" +
"        return r.ok ? await r.json() : { error: await r.text() };\n" +
"      } catch (e) {\n" +
"        return { error: e.message };\n" +
"      }\n" +
"    }\n" +
"\n" +
"    function isConnected(peer) {\n" +
"      if (!peer.lastHeartbeat) return false;\n" +
"      return (Date.now() - peer.lastHeartbeat) < HEARTBEAT_TIMEOUT;\n" +
"    }\n" +
"\n" +
"    function triggerHeartbeatAnimation(addr) {\n" +
"      const indicator = document.querySelector(`[data-peer=\"${addr}\"] .peer-indicator`);\n" +
"      if (indicator) {\n" +
"        indicator.classList.remove('active');\n" +
"        void indicator.offsetWidth;\n" +
"        indicator.classList.add('active');\n" +
"      }\n" +
"\n" +
"      const node = document.querySelector(`[data-node=\"${addr}\"] .heartbeat`);\n" +
"      if (node) {\n" +
"        node.classList.remove('active');\n" +
"        void node.offsetWidth;\n" +
"        node.classList.add('active');\n" +
"      }\n" +
"    }\n" +
"\n" +
"    function renderNetworkViz() {\n" +
"      const viz = document.getElementById('network-viz');\n" +
"      viz.innerHTML = '';\n" +
"\n" +
"      const width = viz.offsetWidth;\n" +
"      const height = viz.offsetHeight;\n" +
"      const centerX = width / 2;\n" +
"      const centerY = height / 2;\n" +
"      const radius = Math.min(width, height) * 0.35;\n" +
"\n" +
"      const allNodes = [nodeData.self, ...nodeData.peers.map(p => p.addr)];\n" +
"      const angleStep = (2 * Math.PI) / allNodes.length;\n" +
"\n" +
"      if (nodeData.state === 'LEADER') {\n" +
"        nodeData.peers.forEach((peer, i) => {\n" +
"          const angle = angleStep * (i + 1);\n" +
"          const x = centerX + radius * Math.cos(angle);\n" +
"          const y = centerY + radius * Math.sin(angle);\n" +
"          const line = document.createElement('div');\n" +
"          line.className = 'connection-line';\n" +
"          if (isConnected(peer)) {\n" +
"            line.classList.add('active');\n" +
"          }\n" +
"          const dx = x - centerX;\n" +
"          const dy = y - centerY;\n" +
"          const length = Math.sqrt(dx * dx + dy * dy);\n" +
"          const angle_deg = Math.atan2(dy, dx) * 180 / Math.PI;\n" +
"          line.style.width = length + 'px';\n" +
"          line.style.left = centerX + 'px';\n" +
"          line.style.top = centerY + 'px';\n" +
"          line.style.transform = `rotate(${angle_deg}deg)`;\n" +
"          viz.appendChild(line);\n" +
"        });\n" +
"      }\n" +
"\n" +
"      const selfPos = nodeData.state === 'LEADER' ? \n" +
"        { x: centerX, y: centerY } : \n" +
"        { x: centerX + radius * Math.cos(0), y: centerY + radius * Math.sin(0) };\n" +
"      \n" +
"      createNode(viz, nodeData.self, nodeData.state, selfPos.x, selfPos.y, true, true);\n" +
"\n" +
"      nodeData.peers.forEach((peer, i) => {\n" +
"        const startAngle = nodeData.state === 'LEADER' ? 0 : 1;\n" +
"        const angle = angleStep * (i + startAngle);\n" +
"        const x = centerX + radius * Math.cos(angle);\n" +
"        const y = centerY + radius * Math.sin(angle);\n" +
"        createNode(viz, peer.addr, 'FOLLOWER', x, y, false, isConnected(peer));\n" +
"      });\n" +
"    }\n" +
"\n" +
"    function createNode(container, id, state, x, y, isSelf, connected) {\n" +
"      const node = document.createElement('div');\n" +
"      node.className = 'node';\n" +
"      node.setAttribute('data-node', id);\n" +
"      node.style.left = x + 'px';\n" +
"      node.style.top = y + 'px';\n" +
"\n" +
"      const stateClass = state.toLowerCase();\n" +
"      const disconnectedClass = connected ? '' : ' disconnected';\n" +
"      node.innerHTML = `\n" +
"        <div class=\"heartbeat ${stateClass}\"></div>\n" +
"        <div class=\"node-circle ${stateClass}${disconnectedClass}\">\n" +
"          <div class=\"node-id\">${isSelf ? '⭐ ' : ''}${id}</div>\n" +
"          <div class=\"node-role\">${state}</div>\n" +
"        </div>\n" +
"      `;\n" +
"\n" +
"      container.appendChild(node);\n" +
"    }\n" +
"\n" +
"    function formatTimeSince(ms) {\n" +
"      if (ms < 1000) return ms + 'ms ago';\n" +
"      if (ms < 60000) return (ms / 1000).toFixed(1) + 's ago';\n" +
"      return (ms / 60000).toFixed(1) + 'm ago';\n" +
"    }\n" +
"\n" +
"    function renderPeerList() {\n" +
"      const list = document.getElementById('peer-list');\n" +
"      if (!nodeData.peers || nodeData.peers.length === 0) {\n" +
"        list.innerHTML = '<div style=\"color: #718096; text-align: center; padding: 20px;\">No peers connected</div>';\n" +
"        return;\n" +
"      }\n" +
"\n" +
"      const now = Date.now();\n" +
"      list.innerHTML = nodeData.peers.map(peer => {\n" +
"        const connected = isConnected(peer);\n" +
"        const disconnectedClass = connected ? '' : ' disconnected';\n" +
"        const indicatorClass = connected ? '' : ' disconnected';\n" +
"        const timeSince = peer.lastHeartbeat ? formatTimeSince(now - peer.lastHeartbeat) : 'never';\n" +
"        \n" +
"        return `\n" +
"        <div class=\"peer-item${disconnectedClass}\" data-peer=\"${peer.addr}\">\n" +
"          <div>\n" +
"            <div class=\"peer-info\">\n" +
"              <div class=\"peer-indicator${indicatorClass}\"></div>\n" +
"              <div class=\"peer-addr\">${peer.addr}</div>\n" +
"            </div>\n" +
"            <div class=\"peer-heartbeat-time\">Last heartbeat: ${timeSince}</div>\n" +
"          </div>\n" +
"          <div class=\"peer-stats\">\n" +
"            <span><strong>Next:</strong> ${peer.nextIndex}</span>\n" +
"            <span><strong>Match:</strong> ${peer.matchIndex}</span>\n" +
"          </div>\n" +
"        </div>\n" +
"      `;\n" +
"      }).join('');\n" +
"    }\n" +
"\n" +
"    async function refreshCollections() {\n" +
"      const collections = await fetchJson('/api/collections');\n" +
"      const listDiv = document.getElementById('collections-list');\n" +
"      \n" +
"      if (collections.error) {\n" +
"        listDiv.innerHTML = '<div style=\"color: #718096;\">Failed to load collections</div>';\n" +
"        return;\n" +
"      }\n" +
"      \n" +
"      if (!collections.collections || collections.collections.length === 0) {\n" +
"        listDiv.innerHTML = '<div style=\"color: #718096;\">No collections yet</div>';\n" +
"        return;\n" +
"      }\n" +
"      \n" +
"      listDiv.innerHTML = collections.collections.map(coll => {\n" +
"        const count = collections.counts ? collections.counts[coll] || 0 : 0;\n" +
"        return `<span class=\"collection-badge\">${coll}<span class=\"collection-count\">${count}</span></span>`;\n" +
"      }).join('');\n" +
"    }\n" +
"\n" +
"    async function refresh() {\n" +
"      const status = await fetchJson('/api/status');\n" +
"      const cluster = await fetchJson('/api/cluster');\n" +
"\n" +
"      if (!status.error) {\n" +
"        document.getElementById('node-id').textContent = status.id || '-';\n" +
"        document.getElementById('node-term').textContent = status.term || '-';\n" +
"        document.getElementById('commit-index').textContent = status.commitIndex || '-';\n" +
"        document.getElementById('last-applied').textContent = status.lastApplied || '-';\n" +
"\n" +
"        const stateElem = document.getElementById('node-state');\n" +
"        const state = (status.state || 'FOLLOWER').toUpperCase();\n" +
"        stateElem.innerHTML = `<span class=\"badge ${state.toLowerCase()}\">${state}</span>`;\n" +
"        nodeData.state = state;\n" +
"      }\n" +
"\n" +
"      if (!cluster.error) {\n" +
"        nodeData.self = cluster.self || 'unknown';\n" +
"        \n" +
"        nodeData.peers = (cluster.peers || []).map(peer => {\n" +
"          const prevHeartbeat = lastHeartbeats[peer.addr];\n" +
"          const newHeartbeat = peer.lastHeartbeat || 0;\n" +
"          \n" +
"          if (newHeartbeat > 0 && newHeartbeat !== prevHeartbeat) {\n" +
"            triggerHeartbeatAnimation(peer.addr);\n" +
"          }\n" +
"          \n" +
"          lastHeartbeats[peer.addr] = newHeartbeat;\n" +
"          return { ...peer, lastHeartbeat: newHeartbeat };\n" +
"        });\n" +
"        \n" +
"        renderNetworkViz();\n" +
"        renderPeerList();\n" +
"      }\n" +
"\n" +
"      const count = document.getElementById('count').value || '50';\n" +
"      const log = await fetchJson('/api/log?count=' + encodeURIComponent(count));\n" +
"      const tbody = document.getElementById('log-tbody');\n" +
"      tbody.innerHTML = '';\n" +
"      (log.entries || []).forEach(e => {\n" +
"        const tr = document.createElement('tr');\n" +
"        tr.innerHTML = `<td>${e.index}</td><td>${e.term}</td><td>${e.data}</td>`;\n" +
"        tbody.appendChild(tr);\n" +
"      });\n" +
"    }\n" +
"\n" +
"    function showResponse(data) {\n" +
"      const respDiv = document.getElementById('clientresp');\n" +
"      respDiv.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);\n" +
"    }\n" +
"\n" +
"    document.getElementById('refresh').addEventListener('click', e => {\n" +
"      e.preventDefault();\n" +
"      refresh();\n" +
"    });\n" +
"\n" +
"    document.getElementById('refresh-collections').addEventListener('click', e => {\n" +
"      e.preventDefault();\n" +
"      refreshCollections();\n" +
"    });\n" +
"\n" +
"    document.getElementById('insertform').addEventListener('submit', async e => {\n" +
"      e.preventDefault();\n" +
"      const collection = document.getElementById('insert-collection').value;\n" +
"      const docText = document.getElementById('insert-document').value;\n" +
"      \n" +
"      try {\n" +
"        const doc = JSON.parse(docText);\n" +
"        const r = await fetch(`/api/documents?collection=${encodeURIComponent(collection)}`, {\n" +
"          method: 'POST',\n" +
"          headers: { 'Content-Type': 'application/json' },\n" +
"          body: JSON.stringify(doc)\n" +
"        });\n" +
"        const result = await r.json();\n" +
"        showResponse(result);\n" +
"        setTimeout(() => { refresh(); refreshCollections(); }, 400);\n" +
"      } catch (err) {\n" +
"        showResponse({ error: err.message });\n" +
"      }\n" +
"    });\n" +
"\n" +
"    document.getElementById('queryform').addEventListener('submit', async e => {\n" +
"      e.preventDefault();\n" +
"      const collection = document.getElementById('query-collection').value;\n" +
"      const limit = parseInt(document.getElementById('query-limit').value) || 100;\n" +
"      const condText = document.getElementById('query-conditions').value;\n" +
"      \n" +
"      try {\n" +
"        const conditions = condText.trim() ? JSON.parse(condText) : {};\n" +
"        const r = await fetch('/api/query', {\n" +
"          method: 'POST',\n" +
"          headers: { 'Content-Type': 'application/json' },\n" +
"          body: JSON.stringify({ collection, conditions, limit })\n" +
"        });\n" +
"        const result = await r.json();\n" +
"        showResponse(result);\n" +
"      } catch (err) {\n" +
"        showResponse({ error: err.message });\n" +
"      }\n" +
"    });\n" +
"\n" +
"    document.getElementById('query-all-btn').addEventListener('click', async e => {\n" +
"      e.preventDefault();\n" +
"      const collection = document.getElementById('query-collection').value;\n" +
"      if (!collection) {\n" +
"        showResponse({ error: 'Collection name is required' });\n" +
"        return;\n" +
"      }\n" +
"      const limit = parseInt(document.getElementById('query-limit').value) || 100;\n" +
"      \n" +
"      try {\n" +
"        const r = await fetch(`/api/documents?collection=${encodeURIComponent(collection)}&limit=${limit}`);\n" +
"        const result = await r.json();\n" +
"        showResponse(result);\n" +
"      } catch (err) {\n" +
"        showResponse({ error: err.message });\n" +
"      }\n" +
"    });\n" +
"\n" +
"    document.getElementById('updateform').addEventListener('submit', async e => {\n" +
"      e.preventDefault();\n" +
"      const collection = document.getElementById('update-collection').value;\n" +
"      const id = document.getElementById('update-id').value;\n" +
"      const updateText = document.getElementById('update-data').value;\n" +
"      \n" +
"      try {\n" +
"        const updates = JSON.parse(updateText);\n" +
"        const r = await fetch(`/api/documents?collection=${encodeURIComponent(collection)}&id=${encodeURIComponent(id)}`, {\n" +
"          method: 'PUT',\n" +
"          headers: { 'Content-Type': 'application/json' },\n" +
"          body: JSON.stringify(updates)\n" +
"        });\n" +
"        const result = await r.json();\n" +
"        showResponse(result);\n" +
"        setTimeout(refresh, 400);\n" +
"      } catch (err) {\n" +
"        showResponse({ error: err.message });\n" +
"      }\n" +
"    });\n" +
"\n" +
"    document.getElementById('deleteform').addEventListener('submit', async e => {\n" +
"      e.preventDefault();\n" +
"      const collection = document.getElementById('delete-collection').value;\n" +
"      const id = document.getElementById('delete-id').value;\n" +
"      \n" +
"      const r = await fetch(`/api/documents?collection=${encodeURIComponent(collection)}&id=${encodeURIComponent(id)}`, {\n" +
"        method: 'DELETE'\n" +
"      });\n" +
"      const result = await r.json();\n" +
"      showResponse(result);\n" +
"      setTimeout(() => { refresh(); refreshCollections(); }, 400);\n" +
"    });\n" +
"\n" +
"    document.getElementById('indexform').addEventListener('submit', async e => {\n" +
"      e.preventDefault();\n" +
"      const collection = document.getElementById('index-collection').value;\n" +
"      const field = document.getElementById('index-field').value;\n" +
"      \n" +
"      const r = await fetch('/api/index', {\n" +
"        method: 'POST',\n" +
"        headers: { 'Content-Type': 'application/json' },\n" +
"        body: JSON.stringify({ collection, field })\n" +
"      });\n" +
"      const result = await r.json();\n" +
"      showResponse(result);\n" +
"      setTimeout(refresh, 400);\n" +
"    });\n" +
"\n" +
"    refresh();\n" +
"    refreshCollections();\n" +
"    setInterval(refresh, 300);\n" +
"    setInterval(refreshCollections, 2000);\n" +
"  </script>\n" +
"</body>\n" +
"</html>\n";
}