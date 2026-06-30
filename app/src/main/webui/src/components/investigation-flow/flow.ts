import { ComponentApi } from "@casehubio/pages-iframe-api";
import type { DataSet } from "@casehubio/pages-iframe-api";

interface FlowNode {
  capabilityTag: string;
  workerId: string;
  trustScoreAtRouting: number | null;
  status: string;
  timestamp: string;
}

interface FlowData {
  nodes: FlowNode[];
  edges: Array<{ from: number; to: number }>;
  parallelGroups: number[][];
}

const app = document.getElementById("app")!;
const api = new ComponentApi();

api.getComponentController(
  undefined,
  (dataSet: DataSet) => {
    const data = extractFlowData(dataSet);
    if (data) {
      render(data);
    } else {
      app.innerHTML = '<p class="flow-empty">No investigation flow data available.</p>';
    }
  },
);

function extractFlowData(dataSet: DataSet): FlowData | null {
  if (!dataSet.data || dataSet.data.length === 0) return null;

  const row = dataSet.data[0];
  if (!row) return null;

  const nodes: FlowNode[] = [];
  const edges: Array<{ from: number; to: number }> = [];
  const parallelGroups: number[][] = [];

  const nodesCol = dataSet.columns.findIndex(c => c.name === "nodes");
  const edgesCol = dataSet.columns.findIndex(c => c.name === "edges");
  const parallelCol = dataSet.columns.findIndex(c => c.name === "parallelGroups");

  if (nodesCol >= 0 && row[nodesCol]) {
    const parsed = typeof row[nodesCol] === "string" ? JSON.parse(row[nodesCol] as string) : row[nodesCol];
    if (Array.isArray(parsed)) nodes.push(...parsed);
  }
  if (edgesCol >= 0 && row[edgesCol]) {
    const parsed = typeof row[edgesCol] === "string" ? JSON.parse(row[edgesCol] as string) : row[edgesCol];
    if (Array.isArray(parsed)) edges.push(...parsed);
  }
  if (parallelCol >= 0 && row[parallelCol]) {
    const parsed = typeof row[parallelCol] === "string" ? JSON.parse(row[parallelCol] as string) : row[parallelCol];
    if (Array.isArray(parsed)) parallelGroups.push(...parsed);
  }

  return { nodes, edges, parallelGroups };
}

function render(data: FlowData): void {
  const { nodes, edges, parallelGroups } = data;
  if (nodes.length === 0) {
    app.innerHTML = '<p class="flow-empty">No stages in this investigation.</p>';
    return;
  }

  const parallelSets = new Map<number, number>();
  parallelGroups.forEach((group, groupIdx) => {
    group.forEach(nodeIdx => parallelSets.set(nodeIdx, groupIdx));
  });

  const container = document.createElement("div");
  container.className = "flow-container";

  const roots = findRoots(nodes, edges);
  const visited = new Set<number>();
  const queue = [...roots];

  while (queue.length > 0) {
    const idx = queue.shift()!;
    if (visited.has(idx)) continue;

    const groupId = parallelSets.get(idx);
    if (groupId !== undefined) {
      const group = parallelGroups[groupId]!.filter(i => !visited.has(i));
      if (group.length > 1) {
        if (container.children.length > 0) {
          container.appendChild(createEdge());
        }
        const parallel = document.createElement("div");
        parallel.className = "flow-parallel";

        const label = document.createElement("div");
        label.className = "flow-parallel__label";
        label.textContent = "parallel";
        container.appendChild(label);

        group.forEach(i => {
          parallel.appendChild(createNode(nodes[i]!));
          visited.add(i);
        });
        container.appendChild(parallel);

        group.forEach(i => {
          edges.filter(e => e.from === i).forEach(e => {
            if (!visited.has(e.to)) queue.push(e.to);
          });
        });
        continue;
      }
    }

    if (container.children.length > 0) {
      container.appendChild(createEdge());
    }
    container.appendChild(createNode(nodes[idx]!));
    visited.add(idx);

    edges.filter(e => e.from === idx).forEach(e => {
      if (!visited.has(e.to)) queue.push(e.to);
    });
  }

  app.innerHTML = "";
  app.appendChild(container);
}

function findRoots(nodes: FlowNode[], edges: Array<{ from: number; to: number }>): number[] {
  const targets = new Set(edges.map(e => e.to));
  const roots: number[] = [];
  for (let i = 0; i < nodes.length; i++) {
    if (!targets.has(i)) roots.push(i);
  }
  return roots.length > 0 ? roots : [0];
}

function createNode(node: FlowNode): HTMLElement {
  const el = document.createElement("div");
  el.className = `flow-node flow-node--${node.status.toLowerCase()}`;

  const cap = document.createElement("div");
  cap.className = "flow-node__capability";
  cap.textContent = node.capabilityTag;
  el.appendChild(cap);

  const worker = document.createElement("div");
  worker.className = "flow-node__worker";
  worker.textContent = node.workerId;
  el.appendChild(worker);

  if (node.trustScoreAtRouting !== null) {
    const trust = document.createElement("div");
    trust.className = "flow-node__trust";
    trust.textContent = `Trust: ${(node.trustScoreAtRouting * 100).toFixed(0)}%`;
    el.appendChild(trust);
  }

  const status = document.createElement("span");
  status.className = `flow-node__status flow-node__status--${node.status.toLowerCase()}`;
  status.textContent = node.status;
  el.appendChild(status);

  return el;
}

function createEdge(): HTMLElement {
  const el = document.createElement("div");
  el.className = "flow-edge";
  return el;
}
