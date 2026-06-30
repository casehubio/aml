import { build, context } from "esbuild";
import { cpSync, existsSync } from "fs";

const isWatch = process.argv.includes("--watch");

const mainOptions = {
  entryPoints: ["src/index.ts"],
  bundle: true,
  outfile: "dist/app.js",
  format: "esm",
  target: "es2020",
  minify: !isWatch,
  sourcemap: isWatch,
};

const flowComponentOptions = {
  entryPoints: ["src/components/investigation-flow/flow.ts"],
  bundle: true,
  outfile: "dist/pages/component/investigation-flow/app.js",
  format: "esm",
  target: "es2020",
  minify: !isWatch,
  sourcemap: isWatch,
};

function copyAssets() {
  cpSync("index.html", "dist/index.html");

  const flowSrc = "src/components/investigation-flow";
  const flowDest = "dist/pages/component/investigation-flow";
  if (existsSync(`${flowSrc}/index.html`)) {
    cpSync(`${flowSrc}/index.html`, `${flowDest}/index.html`);
  }
  if (existsSync(`${flowSrc}/flow.css`)) {
    cpSync(`${flowSrc}/flow.css`, `${flowDest}/flow.css`);
  }
}

if (isWatch) {
  const mainCtx = await context(mainOptions);
  const flowCtx = await context(flowComponentOptions);
  await mainCtx.watch();
  await flowCtx.watch();
  copyAssets();
  console.log("Watching for changes...");
} else {
  await build(mainOptions);
  await build(flowComponentOptions);
  copyAssets();
}
