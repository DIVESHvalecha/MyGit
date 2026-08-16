package org.divesh.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.divesh.agent.CommandExecutor;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class WebServer {

    private final String jarPath;
    private final String workingDir;
    private HttpServer server;

    public WebServer(String workingDir) {
        this.jarPath    = CommandExecutor.detectJarPath();
        this.workingDir = workingDir;
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(port), 0);
        server.createContext("/", new IndexHandler());
        server.createContext("/run", new RunHandler());
        server.start();
        System.out.println("mygit web dashboard running at"
                + " http://localhost:" + port);
        System.out.println("press Ctrl+C to stop");
    }

    class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange)
                throws IOException {
            String html = buildHtml();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders()
                    .set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    class RunHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange)
                throws IOException {

            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            // read request body
            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);

            // parse command from JSON manually
            String command = extractJson(body, "command");

            String output;
            if (command == null || command.isEmpty()) {
                output = "no command provided";
            } else {
                try {
                    CommandExecutor executor =
                            new CommandExecutor(jarPath, workingDir);
                    output = executor.execute(command);
                } catch (Exception e) {
                    output = "error: " + e.getMessage();
                }
            }

            // build JSON response
            String json = "{\"output\":"
                    + toJsonString(output) + "}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders()
                    .set("Content-Type", "application/json");
            exchange.getResponseHeaders()
                    .set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String extractJson(String json, String key) {
            String marker = "\"" + key + "\":\"";
            int start = json.indexOf(marker);
            if (start == -1) return "";
            start += marker.length();
            int end = json.indexOf("\"", start);
            if (end == -1) return "";
            return json.substring(start, end);
        }

        private String toJsonString(String value) {
            return "\"" + value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    + "\"";
        }
    }

    private String buildHtml() {
        return """
<!DOCTYPE html>
<html>
<head>
    <title>mygit dashboard</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: monospace;
            background: #1e1e1e;
            color: #d4d4d4;
            height: 100vh;
            display: flex;
            flex-direction: column;
        }
        header {
            background: #007acc;
            color: white;
            padding: 12px 20px;
            font-size: 18px;
            font-weight: bold;
        }
        .main { display: flex; flex: 1; overflow: hidden; }
        .sidebar {
            width: 200px;
            background: #252526;
            padding: 16px;
            border-right: 1px solid #3c3c3c;
            overflow-y: auto;
        }
        .sidebar h3 {
            color: #9cdcfe;
            margin-bottom: 8px;
            margin-top: 12px;
            font-size: 11px;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        .cmd-btn {
            display: block;
            width: 100%;
            padding: 7px 10px;
            margin-bottom: 3px;
            background: #2d2d2d;
            color: #d4d4d4;
            border: 1px solid #3c3c3c;
            border-radius: 4px;
            cursor: pointer;
            text-align: left;
            font-family: monospace;
            font-size: 12px;
        }
        .cmd-btn:hover {
            background: #37373d;
            border-color: #007acc;
            color: #9cdcfe;
        }
        .cmd-btn.danger:hover {
            border-color: #f44747;
            color: #f44747;
        }
        .output-area {
            flex: 1;
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        .output {
            flex: 1;
            padding: 16px;
            overflow-y: auto;
            background: #1e1e1e;
            font-size: 13px;
            line-height: 1.6;
        }
        .cmd-line { color: #569cd6; }
        .result   { color: #d4d4d4; }
        .error    { color: #f44747; }
        .divider  {
            border-top: 1px solid #3c3c3c;
            margin: 8px 0;
        }
        .input-bar {
            display: flex;
            align-items: center;
            padding: 10px 16px;
            background: #252526;
            border-top: 1px solid #3c3c3c;
            gap: 8px;
        }
        .input-bar span { color: #569cd6; }
        .input-bar input {
            flex: 1;
            background: #1e1e1e;
            border: 1px solid #3c3c3c;
            color: #d4d4d4;
            padding: 7px 12px;
            font-family: monospace;
            font-size: 13px;
            border-radius: 4px;
            outline: none;
        }
        .input-bar input:focus { border-color: #007acc; }
        .input-bar button {
            background: #007acc;
            color: white;
            border: none;
            padding: 7px 14px;
            border-radius: 4px;
            cursor: pointer;
            font-family: monospace;
            font-size: 13px;
        }
        .input-bar button:hover { background: #0062a3; }
        .clear-btn { background: #3c3c3c !important; }
        .clear-btn:hover { background: #505050 !important; }
    </style>
</head>
<body>
<header>⚡ mygit — web dashboard</header>
<div class="main">
    <div class="sidebar">
        <h3>basics</h3>
        <button class="cmd-btn"
            onclick="run('mygit status')">status</button>
        <button class="cmd-btn"
            onclick="run('mygit log --oneline')">log</button>
        <button class="cmd-btn"
            onclick="run('mygit branch')">branches</button>
        <button class="cmd-btn"
            onclick="run('mygit diff')">diff</button>

        <h3>staging</h3>
        <button class="cmd-btn"
            onclick="run('mygit add .')">add all</button>
        <button class="cmd-btn"
            onclick="ask('mygit add ','file to add:')">
            add file</button>

        <h3>commits</h3>
        <button class="cmd-btn"
            onclick="askQ('mygit commit -m ','message:',true)">
            commit</button>
        <button class="cmd-btn"
            onclick="run('mygit stash')">stash</button>
        <button class="cmd-btn"
            onclick="run('mygit stash pop')">stash pop</button>
        <button class="cmd-btn"
            onclick="run('mygit stash list')">stash list</button>

        <h3>branches</h3>
        <button class="cmd-btn"
            onclick="ask('mygit branch ','branch name:')">
            new branch</button>
        <button class="cmd-btn"
            onclick="ask('mygit checkout ','branch to switch:')">
            checkout</button>
        <button class="cmd-btn"
            onclick="ask('mygit checkout -b ','new branch name:')">
            checkout -b</button>
        <button class="cmd-btn"
            onclick="ask('mygit merge ','branch to merge:')">
            merge</button>

        <h3>diff</h3>
        <button class="cmd-btn"
            onclick="run('mygit diff --cached')">
            diff --cached</button>
        <button class="cmd-btn"
            onclick="ask('mygit diff ','e.g. main..feature:')">
            diff branches</button>

        <h3>undo</h3>
        <button class="cmd-btn"
            onclick="run('mygit reset --soft HEAD~1')">
            reset --soft</button>
        <button class="cmd-btn"
            onclick="run('mygit reset --mixed HEAD~1')">
            reset --mixed</button>
        <button class="cmd-btn danger"
            onclick="confirmRun('mygit reset --hard HEAD',
            'Discard ALL changes? This cannot be undone.')">
            reset --hard ⚠</button>
        <button class="cmd-btn"
            onclick="ask('mygit restore --staged ','file:')">
            restore staged</button>

        <h3>inspect</h3>
        <button class="cmd-btn"
            onclick="ask('mygit cat-file -p ','SHA:')">
            cat-file -p</button>
        <button class="cmd-btn"
            onclick="ask('mygit cat-file -t ','SHA:')">
            cat-file -t</button>
        <button class="cmd-btn"
            onclick="ask('mygit hash-object ','file:')">
            hash-object</button>
    </div>

    <div class="output-area">
        <div class="output" id="output">
            <div class="result">
                ⚡ welcome to mygit dashboard</div>
            <div class="result">
                click a command or type below</div>
            <div class="divider"></div>
        </div>
        <div class="input-bar">
            <span>$</span>
            <input type="text" id="cmdInput"
                placeholder="mygit status"
                onkeydown="if(event.key==='Enter')execute()"/>
            <button onclick="execute()">run</button>
            <button class="clear-btn"
                onclick="clearOutput()">clear</button>
        </div>
    </div>
</div>
<script>
    function run(cmd) {
        document.getElementById('cmdInput').value = cmd;
        execute();
    }
    function ask(prefix, prompt) {
        let v = window.prompt(prompt);
        if (v) run(prefix + v);
    }
    function askQ(prefix, prompt, quote) {
        let v = window.prompt(prompt);
        if (!v) return;
        run(quote ? prefix + '"' + v + '"' : prefix + v);
    }
    function confirmRun(cmd, msg) {
        if (window.confirm(msg)) run(cmd);
    }
    function execute() {
        let input = document.getElementById('cmdInput');
        let cmd   = input.value.trim();
        if (!cmd) return;
        addLine('$ ' + cmd, 'cmd-line');
        input.value = '';
        fetch('/run', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({command: cmd})
        })
        .then(r => r.json())
        .then(data => {
            data.output.split('\\n').forEach(line => {
                if (line.trim()) {
                    let cls = line.toLowerCase()
                            .startsWith('error')
                            ? 'error' : 'result';
                    addLine(line, cls);
                }
            });
            addDivider();
            scrollBottom();
        })
        .catch(e => addLine('fetch error: ' + e, 'error'));
    }
    function addLine(text, cls) {
        let o   = document.getElementById('output');
        let div = document.createElement('div');
        div.className = cls;
        div.textContent = text;
        o.appendChild(div);
    }
    function addDivider() {
        let o   = document.getElementById('output');
        let div = document.createElement('div');
        div.className = 'divider';
        o.appendChild(div);
    }
    function clearOutput() {
        document.getElementById('output').innerHTML = '';
    }
    function scrollBottom() {
        let o = document.getElementById('output');
        o.scrollTop = o.scrollHeight;
    }
</script>
</body>
</html>
""";
    }
}