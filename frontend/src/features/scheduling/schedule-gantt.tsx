"use client";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Gantt, Willow, type IApi } from "@svar-ui/react-gantt";
import "@svar-ui/react-gantt/all.css";
import { fromCivil, toCivil, type TaskInput, type Dependency } from "./types";

export default function ScheduleGantt({tasks,dependencies,change,readonly=false}:{tasks:TaskInput[];dependencies:Dependency[];change:(id:string,start:string,end:string)=>void;readonly?:boolean}) {
  const [expanded,setExpanded]=useState(false);
  const dialog=useRef<HTMLDialogElement>(null);
  useEffect(()=>{if(expanded)dialog.current?.showModal();else dialog.current?.close();},[expanded]);
  const latest=useRef({tasks,change});
  useEffect(()=>{latest.current={tasks,change};},[tasks,change]);
  const model=useMemo(()=>tasks.filter(t=>t.state!=="CANCELLED"&&(t.kind==="SUMMARY"||t.startDate&&t.endExclusive)).map(t=>({id:t.id,parent:t.parentId??0,open:true,text:t.title,...(t.startDate&&t.endExclusive?{start:fromCivil(t.startDate),end:fromCivil(t.endExclusive)}:{}),type:t.kind==="MILESTONE"?"milestone":t.kind==="SUMMARY"?"summary":"task"})),[tasks]);
  const links=useMemo(()=>dependencies.map((d,i)=>({id:i+1,source:d.predecessorId,target:d.successorId,type:"e2s"})),[dependencies]);
  const init=useCallback((api:IApi)=> {
    api.intercept("update-task",event=>{
      const current=latest.current.tasks.find(t=>t.id===event.id);
      if(!current||current.kind==="SUMMARY")return false;
      if(event.inProgress)return false;
      if(!event.task.start&&!event.task.end)return false;
      const shift=(date:Date|undefined,days:number)=>{if(!date)return date;const next=new Date(date);next.setDate(next.getDate()+days);return next;};
      const diff=event.diff??0;
      const start=event.task.start?shift(event.task.start,diff):fromCivil(current.startDate!),end=event.task.end?shift(event.task.end,diff):fromCivil(current.endExclusive!);
      if(start&&end)latest.current.change(String(event.id),toCivil(start),toCivil(end));
      return false;
    });
    for(const action of ["add-task","delete-task","move-task","add-link","update-link","delete-link","show-editor"] as const)api.intercept(action,()=>false);
  },[]);
  // Espacio para cabeceras, filas y scroll; no comprimir diez tareas en una altura fija.
  const height=Math.max(560,Math.min(1100,120+model.length*42));
  const chart=<div className="min-h-0 min-w-0 overflow-hidden" style={{height:expanded?"100%":height}} data-testid="schedule-gantt"><Willow><Gantt tasks={model} links={links} init={init} readonly={readonly} cellWidth={50} cellHeight={42} scales={[{unit:"month",step:1,format:(date:Date)=>new Intl.DateTimeFormat("es-CR",{month:"long",year:"numeric"}).format(date)},{unit:"day",step:1,format:"%d"}]} columns={[{id:"text",header:"Tarea",width:220},{id:"start",header:"Inicio",width:110}]}/></Willow></div>;
  return <><div className="mb-3 flex flex-wrap items-center gap-3"><Button type="button" variant="outline" onClick={()=>setExpanded(true)}>Ampliar Gantt</Button><p className="text-sm">{model.length} entregables · Arrastra para mover; ajusta los extremos para cambiar duración.</p></div>{!expanded&&chart}<dialog ref={dialog} aria-label="Vista ampliada del cronograma" onClose={()=>setExpanded(false)} className="fixed inset-4 m-0 h-[calc(100dvh-2rem)] max-h-none w-[calc(100vw-2rem)] max-w-none rounded-xl border border-border bg-white p-4 backdrop:bg-black/50"><div className="flex h-full min-h-0 flex-col gap-3"><div className="flex flex-wrap items-center justify-between gap-3"><h3 className="text-lg font-semibold">Cronograma ampliado</h3><Button type="button" variant="outline" onClick={()=>setExpanded(false)}>Cerrar vista ampliada</Button></div><p className="text-sm">Los movimientos preparan cambios. Cierra esta vista y guarda el cronograma para confirmarlos.</p><div className="min-h-0 flex-1">{expanded&&chart}</div></div></dialog></>;
}
