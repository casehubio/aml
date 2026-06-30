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

function copyComponentAssets() {
  const src = "src/components/investigation-flow";
  const dest = "dist/pages/component/investigation-flow";
  if (existsSync(`${src}/index.html`)) {
    cpSync(`${src}/index.html`, `${dest}/index.html`);
  }
  if (existsSync(`${src}/flow.css`)) {
    cpSync(`${src}/flow.css`, `${dest}/flow.css`);
  }
}

if (isWatch) {
  const mainCtx = await context(mainOptions);
  const flowCtx = await context(flowComponentOptions);
  await mainCtx.watch();
  await flowCtx.watch();
  copyComponentAssets();
  console.log("Watching for changes...");
} else {
  await build(mainOptions);
  await build(flowComponentOptions);
  copyComponentAssets();
}
