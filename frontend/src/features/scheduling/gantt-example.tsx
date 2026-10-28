"use client";
import { Gantt, Willow } from "@svar-ui/react-gantt";
import "@svar-ui/react-gantt/all.css";

// Fixtures descartables de BK-002, sin reglas ni datos de obras reales.
const tasks = [
  { id: 1, text: "Preparación", start: new Date(2026, 9, 7), duration: 3, progress: 100, type: "task" },
  { id: 2, text: "Revisión", start: new Date(2026, 9, 10), duration: 2, progress: 0, type: "task" },
];
const links = [{ id: 1, source: 1, target: 2, type: "e2s" }];
export default function GanttExample() {
  return <div className="h-[440px] overflow-auto" data-testid="gantt-spike"><Willow><Gantt tasks={tasks} links={links} readonly /></Willow></div>;
}
