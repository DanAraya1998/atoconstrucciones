"use client";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { addCivilDays, civilDaysBetween, type TaskInput } from "./types";

export function RescheduleTask({task,prepare,cancel}:{task:TaskInput;prepare:(start:string,end:string)=>void;cancel:()=>void}) {
  const milestone=task.kind==="MILESTONE";
  const [mode,setMode]=useState("dates"),[start,setStart]=useState(task.startDate??""),[end,setEnd]=useState(task.endExclusive??""),[shift,setShift]=useState(0);
  const duration=milestone?0:civilDaysBetween(start,end);
  const nextStart=mode==="shift"?addCivilDays(task.startDate??"",shift):start;
  const nextEnd=milestone?nextStart:mode==="shift"?addCivilDays(task.endExclusive??"",shift):end;
  const valid=Boolean(nextStart&&nextEnd&&(milestone||civilDaysBetween(nextStart,nextEnd)>0));
  return <form aria-label={`Reprogramar ${task.title}`} className="grid gap-4 rounded-xl border border-border p-4" onSubmit={e=>{e.preventDefault();if(valid)prepare(nextStart,nextEnd);}}><h3 className="text-lg font-semibold">Reprogramar {task.title}</h3><label>Cómo reprogramar<select value={mode} onChange={e=>setMode(e.target.value)}><option value="dates">Elegir fechas{milestone?"":" y duración"}</option><option value="shift">Desplazar en días calendario</option></select></label>
    {mode==="shift"?<label>Desplazamiento en días<input type="number" required min={-3650} max={3650} step={1} value={shift} onChange={e=>setShift(e.target.valueAsNumber)}/><span className="text-sm">Positivo: después. Negativo: antes. Conserva la duración.</span></label>:<><label>{milestone?"Nueva fecha del hito":"Nuevo inicio"}<input type="date" required value={start} onChange={e=>{const value=e.target.value;setStart(value);setEnd(milestone?value:addCivilDays(value,Number.isFinite(duration)&&duration>0?duration:1));}}/></label>{!milestone&&<><p className="text-sm">Cambiar el inicio conserva la duración. También puedes ajustar el último día o la cantidad de días.</p><label>Último día de trabajo<input type="date" required min={start} value={addCivilDays(end,-1)} onChange={e=>setEnd(addCivilDays(e.target.value,1))}/></label><label>Duración en días calendario<input type="number" required min={1} step={1} value={Number.isFinite(duration)?duration:""} onChange={e=>setEnd(addCivilDays(start,e.target.valueAsNumber))}/></label></>}</>}
    {valid&&<p role="status">Propuesta: {nextStart}{!milestone&&` → ${addCivilDays(nextEnd,-1)} · ${civilDaysBetween(nextStart,nextEnd)} días calendario`}</p>}
    <p className="text-sm">Las dependencias se validan al guardar. Las demás tareas conservan sus fechas. Reprogramar requiere revisar los pesos aprobados.</p><div className="flex flex-wrap gap-3"><Button disabled={!valid}>Preparar reprogramación</Button><Button type="button" variant="outline" onClick={cancel}>Cancelar reprogramación</Button></div>
  </form>;
}
