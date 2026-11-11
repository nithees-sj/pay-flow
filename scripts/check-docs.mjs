import { readFileSync, readdirSync, existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
for (const name of ["architecture", "data-model", "api", "plan"]) {
  const path = `docs/${name}.md`;
  if (!existsSync(path)) throw new Error(`Missing ${path}`);
  const content = readFileSync(path, "utf8");
  for (const match of content.matchAll(/```json\n([\s\S]*?)\n```/g))
    JSON.parse(match[1]);
}
if (
  readdirSync("docs/decisions").filter((name) => name.endsWith(".md"))
    .length !== 6
)
  throw new Error("Expected six ADRs");
const architecture = readFileSync("docs/architecture.md", "utf8");
if (
  !architecture.includes("sequenceDiagram") ||
  !architecture.includes("flowchart")
)
  throw new Error("Missing architecture diagrams");
console.log(
  "Documentation inventory, diagram presence and JSON examples: PASS",
);

const markdownFiles = [
  "README.md",
  ...readdirSync("docs", { recursive: true })
    .filter((name) => name.endsWith(".md"))
    .map((name) => `docs/${name}`),
];
for (const file of markdownFiles) {
  for (const match of readFileSync(file, "utf8").matchAll(
    /!?\[[^\]]*\]\(([^)]+)\)/g,
  )) {
    const target = match[1].split("#")[0];
    if (
      target &&
      !/^[a-z]+:/.test(target) &&
      !existsSync(resolve(dirname(file), target))
    )
      throw new Error(`Broken local link in ${file}: ${target}`);
  }
}
console.log("Local Markdown links: PASS");
