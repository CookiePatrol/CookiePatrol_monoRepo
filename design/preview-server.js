const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

const files = new Map([
  ["/", ["cookie-patrol-concept.html", "text/html; charset=utf-8"]],
  ["/index.html", ["cookie-patrol-concept.html", "text/html; charset=utf-8"]],
  ["/cookie-patrol-concept.html", ["cookie-patrol-concept.html", "text/html; charset=utf-8"]],
  ["/cookie-patrol-preview.png", ["cookie-patrol-preview.png", "image/png"]],
]);

const server = http.createServer((request, response) => {
  const route = new URL(request.url, "http://localhost").pathname;
  const entry = files.get(route);
  if (!entry || !["GET", "HEAD"].includes(request.method)) {
    response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    response.end("Not found");
    return;
  }

  const [filename, contentType] = entry;
  const content = fs.readFileSync(path.join(__dirname, filename));
  response.writeHead(200, {
    "Content-Type": contentType,
    "Content-Length": content.length,
    "Cache-Control": "no-store",
  });
  response.end(request.method === "HEAD" ? undefined : content);
});

const port = Number(process.env.COOKIE_PATROL_PREVIEW_PORT || 8765);
server.listen(port, "127.0.0.1", () => {
  process.stdout.write(`Cookie Patrol design preview: http://localhost:${port}/\n`);
});
