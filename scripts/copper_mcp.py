"""Small stdlib client for the local Copper Golem Fabric SSE test endpoint."""
import json
import queue
import threading
import urllib.request

class CopperMcp:
    def __init__(self, base="http://127.0.0.1:8089"):
        self.base = base
        self.events = queue.Queue()
        self.stream = urllib.request.urlopen(base + "/sse", timeout=30)
        self.next_id = 0
        threading.Thread(target=self._read, daemon=True).start()
        _, endpoint = self.events.get(timeout=10)
        self.endpoint = base + endpoint
        self.request("initialize", {"protocolVersion": "2024-11-05", "capabilities": {}, "clientInfo": {"name": "minigamelib-qa", "version": "1"}})
        self._post({"jsonrpc": "2.0", "method": "notifications/initialized"})

    def _read(self):
        event, data = "", []
        try:
            for raw in self.stream:
                line = raw.decode().rstrip("\r\n")
                if line.startswith("event:"): event = line[6:].strip()
                elif line.startswith("data:"): data.append(line[5:].strip())
                elif not line and data:
                    self.events.put((event, "\n".join(data)))
                    event, data = "", []
        except (OSError, ValueError):
            pass

    def _post(self, message):
        request = urllib.request.Request(self.endpoint, json.dumps(message).encode(), {"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=30) as response: response.read()

    def request(self, method, params=None):
        self.next_id += 1
        self._post({"jsonrpc": "2.0", "id": self.next_id, "method": method, "params": params or {}})
        while True:
            _, raw = self.events.get(timeout=30)
            message = json.loads(raw)
            if message.get("id") == self.next_id:
                if "error" in message: raise RuntimeError(message["error"])
                return message["result"]

    def call(self, name, **args):
        result = self.request("tools/call", {"name": name, "arguments": args})
        if result.get("isError"): raise RuntimeError(result)
        return result

    def close(self): self.stream.close()

if __name__ == "__main__":
    import sys
    client = CopperMcp()
    print(json.dumps(client.call(sys.argv[1], **(json.loads(sys.argv[2]) if len(sys.argv) > 2 else {})), ensure_ascii=False))
