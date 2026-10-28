"use client";
import type { components } from "@/lib/api/schema";
import type { Actor } from "@/lib/api/transport";
import { ActionForm,Field,useData,text,money,send } from "@/features/projects/shared";
import { Amount,label,type Expense } from "@/features/finance/shared";
import { ExpenseFields } from "./expense-fields";
export function expenseBody(d:FormData) {
  return {supplierId:text(d,"supplier")||null,supplierException:text(d,"supplierException"),accruedOn:text(d,"date"),category:text(d,"category"),description:text(d,"description"),net:money(d,"net"),tax:money(d,"tax"),differenceReason:text(d,"difference"),evidenceDocumentId:text(d,"evidence")||null,
    allocations:Array.from({length:Number(text(d,"allocationCount"))},(_,i)=>({projectId:text(d,`project${i}`),extraId:text(d,`extra${i}`)||null,net:money(d,`net${i}`),tax:money(d,`tax${i}`)}))};
}
export function ExpensesPanel({user,projectId}:{user:Actor;projectId:string}) {
  const data=useData<Expense[]>(user,`/api/v1/projects/${projectId}/expenses`),manager=user.roles.includes("GERENTE");
  return <div className="space-y-4"><h3 className="text-xl font-semibold">Gastos y costos</h3><ActionForm title="Registrar gasto" submit="Registrar propuesta" run={(d,k)=>send("/api/v1/expenses","POST",{...expenseBody(d),fiscalKey:text(d,"fiscalKey")||null},k)}><ExpenseFields user={user} projectId={projectId}/><Field label="Clave fiscal si existe factura en PDF o imagen" name="fiscalKey" maxLength={50} required={false}/></ActionForm>
    {data.error&&<p role="alert">No se pudieron consultar los gastos.</p>}{data.data?.map(e=><article className="panel space-y-3" key={e.id}><h4 className="font-semibold">{e.description} · {label(e.state)}</h4><dl className="grid gap-2 text-sm sm:grid-cols-2"><div><dt>Porción de esta obra</dt><dd><Amount value={e.allocatedTotal}/></dd></div><div><dt>Costo reconocido</dt><dd><Amount value={e.recognizedCost}/></dd></div><div><dt>Pagos aplicados</dt><dd><Amount value={e.appliedPayments}/></dd></div><div><dt>Deuda pendiente</dt><dd><Amount value={e.due}/></dd></div></dl>
      {["REGISTERED","IN_REVIEW"].includes(e.state)&&<EditExpense user={user} projectId={projectId} expense={e}/>}
      {["REGISTERED","IN_REVIEW"].includes(e.state)&&<ActionForm title="Revisar gasto" submit="Guardar revisión" run={(d,k)=>send(`/api/v1/expenses/${e.id}/review`,"POST",{reason:text(d,"reason")},k,e.version)}><Field label="Motivo de revisión" name="reason" maxLength={1000}/></ActionForm>}
      {manager&&["REGISTERED","IN_REVIEW"].includes(e.state)&&<><ActionForm title="Validar costo" submit="Validar imputaciones" run={(d,k)=>send(`/api/v1/expenses/${e.id}/validate`,"POST",{reason:text(d,"reason")},k,e.version)}><Field label="Revisión de emisor, receptor, impuestos y diferencias; excepción si no hay factura" name="reason" maxLength={1000}/></ActionForm><ActionForm title="Rechazar gasto" submit="Rechazar gasto" run={(d,k)=>send(`/api/v1/expenses/${e.id}/reject`,"POST",{reason:text(d,"reason")},k,e.version)}><Field label="Motivo del rechazo" name="reason" maxLength={1000}/></ActionForm></>}
      {manager&&e.state==="VALIDATED"&&<ActionForm title="Revertir costo reconocido" submit="Registrar reversión" run={(d,k)=>send(`/api/v1/expenses/${e.id}/reverse`,"POST",{reason:text(d,"reason")},k,e.version)}><Field label="Motivo de reversión" name="reason" maxLength={1000}/><p className="text-sm">La reversión conserva el original y los pagos; una devolución real se confirma en caja.</p></ActionForm>}
    </article>)}
  </div>;
}

function EditExpense({user,projectId,expense}:{user:Actor;projectId:string;expense:Expense}) {
  const data=useData<components["schemas"]["Expense"]>(user,`/api/v1/expenses/${expense.id}`);
  if(!data.data)return null;const e=data.data;
  return <ActionForm key={e.version} title="Corregir propuesta de gasto" submit="Guardar nueva revisión" run={(d,k)=>send(`/api/v1/expenses/${e.id}`,"PATCH",{...expenseBody(d),voucherId:e.voucherId,fiscalKey:e.fiscalKey,originalExpenseId:e.originalExpenseId},k,e.version)}><ExpenseFields user={user} projectId={projectId} initial={e}/></ActionForm>;
}
