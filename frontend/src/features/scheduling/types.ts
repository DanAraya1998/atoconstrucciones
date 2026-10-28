import type { components } from "@/lib/api/schema";
export type Schedule = components["schemas"]["Schedule"];
export type TaskInput = components["schemas"]["ScheduleTaskInput"];
export type Dependency = components["schemas"]["ScheduleDependency"];
export type Progress = components["schemas"]["ProgressSummary"];
export type AssignedTasks = components["schemas"]["AssignedTasks"];
export function taskInput(t:Schedule["tasks"][number]):TaskInput {
  return {id:t.id,parentId:t.parentId,kind:t.kind,title:t.title,startDate:t.startDate,endExclusive:t.endExclusive,state:t.state,administrative:t.administrative,clientVisible:t.clientVisible,extraId:t.extraId,supplierIds:t.supplierIds};
}
export function fromCivil(value:string) {const [y,m,d]=value.split("-").map(Number);return new Date(y,m-1,d);}
export function toCivil(value:Date) {return `${value.getFullYear()}-${String(value.getMonth()+1).padStart(2,"0")}-${String(value.getDate()).padStart(2,"0")}`;}
// Aritmética de fechas civiles: no sumar milisegundos sobre la zona del navegador.
function civilOrdinal(value:string) {
  if(!/^\d{4}-\d{2}-\d{2}$/.test(value))return NaN;
  const [y,m,d]=value.split("-").map(Number),date=new Date(0);date.setUTCFullYear(y,m-1,d);date.setUTCHours(0,0,0,0);
  return date.toISOString().slice(0,10)===value?date.getTime()/86400000:NaN;
}
export function civilDaysBetween(start:string,end:string) {return civilOrdinal(end)-civilOrdinal(start);}
export function addCivilDays(value:string,days:number) {
  const ordinal=civilOrdinal(value);
  if(!Number.isFinite(ordinal)||!Number.isSafeInteger(days))return "";
  const date=new Date((ordinal+days)*86400000);
  return Number.isFinite(date.getTime())&&date.getUTCFullYear()>=1&&date.getUTCFullYear()<=9999?date.toISOString().slice(0,10):"";
}
